package com.inha.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.inha.model.ApiClient;
import com.inha.model.ApiException;
import com.inha.model.ApiKey;
import com.inha.model.Area;
import com.inha.model.CourseStore;
import com.inha.model.CoursePlanner;
import com.inha.model.DayForecastVO;
import com.inha.model.ForecastStitcher;
import com.inha.model.MapJson;
import com.inha.model.RecommendVO;
import com.inha.model.RegionEvidence;
import com.inha.model.TenDayVO;
import com.inha.model.TravelScore;
import com.inha.model.ImageVO;
import com.inha.model.IntroFields;
import com.inha.model.Recommender;
import com.inha.model.TourDAO;
import com.inha.model.TourVO;
import com.inha.model.WeatherDAO;
import com.inha.model.WeatherVO;

// 관광지 상세 → 그 관광지 좌표 그대로 기상청 날씨 조회 → 이 곳 맞춤 조언
// 공통정보 · 소개 · 사진은 동시에 부르고, 좌표가 나오면 주변 관광지 · 날씨도 동시에 부른다
@WebServlet("/detail")
public class DetailController extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int NEARBY_RADIUS_M = 5000;  // 주변 반경 5km
	private static final int NEARBY_COUNT = 6;        // 주변 관광지 6곳
	private static final int GALLERY_MAX = 12;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");

		String id = request.getParameter("id");
		String type = request.getParameter("type"); // 목록에서 알고 온 관광 타입 (없어도 됨)
		int day = Area.parseDay(request.getParameter("day"));
		String area = request.getParameter("area");
		String sigungu = request.getParameter("sigungu");
		request.setAttribute("selDay", day);
		request.setAttribute("days", Area.dayOptions());
		request.setAttribute("dayLabel", Area.dayOptions().get(day)[1]);
		request.setAttribute("selArea", Area.isValid(area) ? area : "");
		request.setAttribute("selSigungu", sigungu != null && sigungu.matches("\\d{1,5}") ? sigungu : "");
		request.setAttribute("mapJson", "{\"spot\":null,\"nearby\":[]}"); // 지도용 JSON (항상 있음)

		if (id == null || !id.matches("\\d{1,12}")) {
			request.setAttribute("error", "관광지 번호가 올바르지 않아요.");
			request.getRequestDispatcher("/WEB-INF/views/detail.jsp").forward(request, response);
			return;
		}
		String typeId = type != null && IntroFields.of(type).length > 0 ? type : "";

		try {
			String key = ApiKey.load(getServletContext());
			TourDAO tour = new TourDAO(key);
			WeatherDAO weatherDao = new WeatherDAO(key);

			// ① 독립 호출을 한꺼번에 시작: 공통정보 · 소개(타입을 알면) · 사진
			CompletableFuture<TourVO> fCommon = ApiClient.async(() -> tour.detail(id));
			CompletableFuture<Map<String, String>> fIntro = typeId.isEmpty()
					? ApiClient.then(fCommon, s -> s == null ? new LinkedHashMap<String, String>() : tour.intro(id, s.getContentTypeId()))
					: ApiClient.async(() -> tour.intro(id, typeId));
			CompletableFuture<List<ImageVO>> fImages = ApiClient.async(() -> tour.images(id, GALLERY_MAX));

			// ② 좌표가 나오면 바로 주변(관광지 12 · 문화시설 14)과 날씨를 동시에
			CompletableFuture<List<TourVO>> fNear12 = ApiClient.then(fCommon, s -> nearbyOf(tour, s, "12"));
			CompletableFuture<List<TourVO>> fNear14 = ApiClient.then(fCommon, s -> nearbyOf(tour, s, "14"));
			CompletableFuture<WeatherVO> fWeather = ApiClient.then(fCommon,
					s -> s == null || !s.isHasLocation() ? null : weatherDao.forecast(s.getLat(), s.getLon(), day));

			// 공통정보가 안 되면 화면을 못 만든다
			TourVO spot = ApiClient.await(fCommon);
			if (spot == null) {
				request.setAttribute("error", "관광지 정보를 찾지 못했어요.");
			} else {
				request.setAttribute("spot", spot);

				// 새 API 자료(미세먼지 · 자외선 · 일출몰 · 붐빔 · 특보)는 풀 밖(기본 풀)에서 따로 시작해 아래 호출들과 겹쳐 기다린다
				final String evSido = Area.isValid(spot.getSidoCode()) ? spot.getSidoCode() : (Area.isValid(area) ? area : "");
				final String evSigungu = spot.getSigunguCode() != null && !spot.getSigunguCode().isEmpty() ? spot.getSigunguCode()
						: (sigungu != null && sigungu.matches("\\d{1,5}") ? sigungu : "");
				final TourVO fSpot = spot;
				final LocalDate date = Area.dayOf(day);
				CompletableFuture<RegionEvidence> fEv = ApiClient.asyncOuter(
						() -> RegionEvidence.load(key, evSido, evSigungu, fSpot.getAddr(), fSpot.getLat(), fSpot.getLon(), date));

				// 소개 (실패해도 나머지는 보여 줌)
				Map<String, String> intro = new LinkedHashMap<>();
				try {
					intro = ApiClient.await(fIntro);
					if (!typeId.isEmpty() && !typeId.equals(spot.getContentTypeId())) {
						intro = tour.intro(id, spot.getContentTypeId()); // 주소의 타입이 틀렸으면 맞는 타입으로 다시
					}
				} catch (ApiException e) {
					request.setAttribute("introError", e.getMessage());
				}
				request.setAttribute("intro", intro);
				// 행사는 기간(시작일 · 종료일)을 기억해 둔다: 담을 때 CourseItem 에 붙고, 고른 날이 기간 밖이면 아래에서 알린다
				String evNotice = "";
				if (spot.isEvent()) {
					String evStart = intro.getOrDefault("시작일", "").replace(".", "");
					String evEnd = intro.getOrDefault("종료일", "").replace(".", "");
					CourseStore.rememberEventPeriod(request.getSession(), spot.getContentId(), evStart, evEnd);
					evNotice = TourVO.eventNotice(evStart, evEnd, date);
				}

				// 사진 갤러리
				List<ImageVO> gallery = new ArrayList<>();
				try {
					gallery = ApiClient.await(fImages);
				} catch (ApiException e) {
					request.setAttribute("galleryError", e.getMessage());
				}
				request.setAttribute("gallery", gallery);

				// 주변 관광지: 둘 중 하나만 실패해도 나머지로 보여 준다
				List<TourVO> n12 = null, n14 = null;
				ApiException nearErr = null;
				try { n12 = ApiClient.await(fNear12); } catch (ApiException e) { nearErr = e; }
				try { n14 = ApiClient.await(fNear14); } catch (ApiException e) { nearErr = e; }
				if (n12 == null && n14 == null && nearErr != null && spot.isHasLocation()) {
					request.setAttribute("nearbyError", nearErr.getMessage());
				}
				List<TourVO> near = TourDAO.mergeByDist(n12, n14, id, NEARBY_COUNT);
				// 위치기반 조회가 관광지 · 문화시설을 거의 못 찾아서, 모자라면 지역 목록(캐시된 것)에서 거리를 재서 보충
				if (near.size() < NEARBY_COUNT && spot.isHasLocation()) {
					near = fillFromAreaList(tour, spot, area, sigungu, near);
				}
				request.setAttribute("nearby", near);
				String mapArea = Area.isValid(area) ? area : "";
				String mapGu = sigungu != null && sigungu.matches("\\d{1,5}") ? sigungu : "";
				request.setAttribute("mapJson", MapJson.detail(spot, near, null, day, mapArea, mapGu));

				// 날씨 (API ② 에 관광지 좌표 그대로). 0~2일 뒤는 단기예보, 3~10일 뒤는 단기 + 중기를 이어 붙인 값
				WeatherVO weather = null;
				String weatherErr = "";
				boolean midDay = false;
				DayForecastVO dayFc = null;
				if (spot.isHasLocation()) {
					if (day <= 2) {
						try {
							weather = ApiClient.await(fWeather);
						} catch (ApiException e) {
							weatherErr = e.getMessage();
						}
					} else {
						TenDayVO ten = new ForecastStitcher(key).tenDays(spot.getLat(), spot.getLon(), evSido, evSigungu);
						dayFc = ten.getDay(day);
						weather = dayFc.getWeather();
						midDay = DayForecastVO.MID.equals(dayFc.getSource());
						if (weather == null) weatherErr = dayFc.getNote();
						request.setAttribute("dayFc", dayFc);
						request.setAttribute("midNote", dayFc.getNote());
					}
				} else {
					weatherErr = "이 관광지는 좌표 정보가 없어 날씨를 조회하지 못했어요.";
				}
				request.setAttribute("midDay", midDay);

				// 새 API 자료를 모은다 (통째로 실패하면 null → 점수 칩이 모두 "자료 없음")
				RegionEvidence ev = null;
				try {
					ev = fEv.join();
				} catch (Exception e) {
					e.printStackTrace();
				}
				PlanController.putEvidence(request, ev, day);
				int airGrade = ev != null && ev.airView(day) != null ? ev.airView(day).getGrade() : 0;

				// 이 곳 · 이 날의 여행 지수 (붐빔은 관광지 이름이 집중률 목록과 맞을 때만 이 곳 값, 아니면 시군구 평균)
				Double crowd = ev == null ? null : ev.crowdOfSpot(spot.getTitle(), day);
				spot.setCrowdRate(crowd);
				spot.setScore(TravelScore.score(ev, day, weather, spot.isIndoor(), crowd));
				request.setAttribute("spotScore", spot.getScore());

				if (weather != null) {
					RecommendVO rec = Recommender.recommend(weather, airGrade);
					request.setAttribute("weather", weather);
					request.setAttribute("rec", rec);
					request.setAttribute("tip", Recommender.spotTip(spot, weather));
					String best = midDay
							? CoursePlanner.bestTimeMid(spot, dayFc)
							: CoursePlanner.bestTime(spot, weather, day, LocalTime.now(ZoneId.of("Asia/Seoul")));
					if (spot.isEvent()) {
						// 축제 · 행사는 정해진 시간에 열리니 「언제 가면 좋은지」보다 행사 시간을 먼저 알려 준다
						String when = intro.get("공연 시간");
						String whenText = when == null || when.isEmpty() ? "행사 시간은 위 이용 안내를 확인하세요." : "행사는 " + when + "에 열려요.";
						if (!evNotice.isEmpty()) whenText = evNotice;   // 기간 밖이면 시간 대신 이것부터
						best = whenText + " 날씨만 보면 " + best;
					}
					request.setAttribute("bestTime", best);
				} else {
					request.setAttribute("weatherError", weatherErr);
				}
				request.setAttribute("mapJson", MapJson.detail(spot, near, weather, day, mapArea, mapGu));
			}
		} catch (ApiException e) {
			request.setAttribute("error", e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			request.setAttribute("error", "처리 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요.");
		}
		request.getRequestDispatcher("/WEB-INF/views/detail.jsp").forward(request, response);
	}

	// 같은 지역(주소의 area · sigungu, 없으면 이 관광지의 법정동 코드)의 관광지 · 문화시설 목록에서 반경 5km 안의 곳을 더한다
	// 목록 화면과 같은 요청(16곳 · 8곳)이라 목록을 본 직후면 캐시에 맞아서 API 호출이 늘지 않는다
	private List<TourVO> fillFromAreaList(TourDAO tour, TourVO spot, String area, String sigungu, List<TourVO> near) {
		String sido = Area.isValid(area) ? area : spot.getSidoCode();
		String gu = Area.isValid(area) ? (sigungu != null && sigungu.matches("\\d{1,5}") ? sigungu : "") : spot.getSigunguCode();
		if (!Area.isValid(sido)) return near;
		CompletableFuture<List<TourVO>> a12 = ApiClient.async(() -> tour.areaList(sido, gu, "12", 16));
		CompletableFuture<List<TourVO>> a14 = ApiClient.async(() -> tour.areaList(sido, gu, "14", 8));
		List<TourVO> pool = new ArrayList<>();
		try { pool.addAll(ApiClient.await(a12)); } catch (ApiException e) { /* 보충이라 실패해도 그대로 */ }
		try { pool.addAll(ApiClient.await(a14)); } catch (ApiException e) { /* 위와 같음 */ }
		List<TourVO> extra = TourDAO.withinRadius(pool, spot.getLat(), spot.getLon(), NEARBY_RADIUS_M, near, spot.getContentId());
		List<TourVO> all = new ArrayList<>(near);
		all.addAll(extra);
		all.sort((x, y) -> Integer.compare(x.getDistM(), y.getDistM()));
		return all.size() > NEARBY_COUNT ? new ArrayList<>(all.subList(0, NEARBY_COUNT)) : all;
	}

	// 좌표가 있으면 반경 5km 안 같은 타입 관광지를 가까운 순으로
	private List<TourVO> nearbyOf(TourDAO tour, TourVO spot, String typeId) throws ApiException {
		if (spot == null || !spot.isHasLocation()) return new ArrayList<>();
		return tour.nearby(spot.getLat(), spot.getLon(), NEARBY_RADIUS_M, typeId, 8);
	}
}
