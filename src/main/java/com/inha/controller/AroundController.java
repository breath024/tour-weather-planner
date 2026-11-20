package com.inha.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inha.model.ApiClient;
import com.inha.model.ApiException;
import com.inha.model.ApiKey;
import com.inha.model.Area;
import com.inha.model.CourseStore;
import com.inha.model.DayForecastVO;
import com.inha.model.ForecastStitcher;
import com.inha.model.RegionEvidence;
import com.inha.model.RoadRoute;
import com.inha.model.TravelScore;
import com.inha.model.GridConverter;
import com.inha.model.MapJson;
import com.inha.model.NearbyRoute;
import com.inha.model.RateLimiter;
import com.inha.model.RecommendVO;
import com.inha.model.Recommender;
import com.inha.model.TourDAO;
import com.inha.model.TourVO;
import com.inha.model.WeatherDAO;
import com.inha.model.WeatherVO;

// 지도 탐색 JSON 창구: GET /api/around?lat=&lon=&day=&type=
// 누른 지점 주변 관광지(TourAPI locationBasedList2, 반경 5km)와 그 지점의 날씨(기상청)를 동시에 불러 하나의 JSON 으로 돌려준다
@WebServlet("/api/around")
public class AroundController extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int RADIUS_M = 5000;
	private static final int MAX_SPOTS = 12;     // 응답에 담는 관광지 수
	private static final int SPARSE_BELOW = 6;   // 이보다 적으면 지역 목록으로 보충 (위치기반 조회가 관광지를 거의 못 찾아서)

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		// 화면(/explore)이 먼저 만든 세션이 있어야 한다. 쿠키 없이 마구 부르면 세션만 쌓이므로 새로 만들지 않는다
		HttpSession session = request.getSession(false);
		if (session == null) {
			write(response, 403, error("NO_SESSION", "화면을 새로고침한 뒤 다시 눌러 주세요."));
			return;
		}

		// ① 남용 방지: 세션당 분당 20회 · IP 당 30회 · 서버 전체 40회
		int wait = RateLimiter.tryAcquire(session, request.getRemoteAddr());
		if (wait > 0) {
			response.setHeader("Retry-After", String.valueOf(wait));
			JsonObject o = error("RATE_LIMIT", "요청이 너무 많아요. " + wait + "초 뒤에 다시 눌러 주세요.");
			o.addProperty("retryAfter", wait);
			write(response, 429, o);
			return;
		}

		// ② 파라미터 확인 (한국 범위만, 소수 2자리로 반올림 → 같은 동네 반복 클릭은 캐시에 맞는다)
		double lat, lon;
		try {
			lat = Double.parseDouble(request.getParameter("lat"));
			lon = Double.parseDouble(request.getParameter("lon"));
			if (!Double.isFinite(lat) || !Double.isFinite(lon)) throw new NumberFormatException();
		} catch (Exception e) { // null 이거나 숫자가 아님
			write(response, 400, error("BAD_PARAM", "위도(lat)와 경도(lon)를 숫자로 보내 주세요."));
			return;
		}
		if (lat < 33 || lat > 39 || lon < 124 || lon > 132) {
			write(response, 400, error("OUT_OF_RANGE", "한국 안(위도 33~39, 경도 124~132)만 조회할 수 있어요."));
			return;
		}
		lat = Math.round(lat * 100) / 100.0;
		lon = Math.round(lon * 100) / 100.0;

		String dayParam = request.getParameter("day");
		int day = 0;
		if (dayParam != null && !dayParam.isEmpty()) {
			if (!dayParam.matches("([0-9]|10)")) {
				write(response, 400, error("BAD_PARAM", "day 는 0(오늘)부터 10(10일 뒤)까지 숫자예요."));
				return;
			}
			day = Integer.parseInt(dayParam);
		}
		String type = request.getParameter("type");
		if (type == null || type.isEmpty() || type.equals("all")) type = "all";
		if (!type.equals("all") && !type.equals("12") && !type.equals("14")) {
			write(response, 400, error("BAD_PARAM", "type 은 12(관광지) · 14(문화시설) · all 중 하나예요."));
			return;
		}

		try {
			String key = ApiKey.load(getServletContext());
			TourDAO tour = new TourDAO(key);
			WeatherDAO weatherDao = new WeatherDAO(key);
			final double fLat = lat, fLon = lon;
			final int fDay = day;
			boolean want12 = !type.equals("14");
			boolean want14 = !type.equals("12");

			// ③ 독립 호출을 동시에: 기상청 예보 · 주변 관광지(12) · 주변 문화시설(14)
			CompletableFuture<WeatherVO> fWeather = fDay <= 2 ? ApiClient.async(() -> weatherDao.forecast(fLat, fLon, fDay)) : null;
			CompletableFuture<List<TourVO>> f12 = want12 ? ApiClient.async(() -> tour.nearby(fLat, fLon, RADIUS_M, "12", 20)) : null;
			CompletableFuture<List<TourVO>> f14 = want14 ? ApiClient.async(() -> tour.nearby(fLat, fLon, RADIUS_M, "14", 10)) : null;

			WeatherVO weather = null;
			String weatherError = "";

			List<TourVO> a = null, b = null;
			ApiException tourErr = null;
			if (f12 != null) { try { a = ApiClient.await(f12); } catch (ApiException e) { tourErr = e; } }
			if (f14 != null) { try { b = ApiClient.await(f14); } catch (ApiException e) { tourErr = e; } }
			boolean tourFailed = (f12 == null || a == null) && (f14 == null || b == null);

			List<TourVO> spots = TourDAO.mergeByDist(a, b, "", MAX_SPOTS);
			boolean supplemented = false;
			if (!tourFailed && spots.size() < SPARSE_BELOW) {
				// 위치기반 조회가 관광지 · 문화시설을 거의 못 찾는다 → 가까운 지역 목록에서 거리를 재서 보충
				List<TourVO> extra = supplement(tour, fLat, fLon, spots, want12, want14);
				if (!extra.isEmpty()) {
					supplemented = true;
					spots = new ArrayList<>(spots);
					spots.addAll(extra);
					spots.sort((x, y) -> Integer.compare(x.getDistM(), y.getDistM()));
					if (spots.size() > MAX_SPOTS) spots = new ArrayList<>(spots.subList(0, MAX_SPOTS));
				}
			}

			// ③-2 지역(시도 · 시군구)은 찾은 관광지의 법정동 코드로 안다. 새 API 자료는 따로 시작해 날씨와 겹쳐 기다린다
			//      (RegionEvidence.load 는 풀 안에서 다른 작업을 기다리므로 「기다리는 작업」 전용 풀에서 돌린다)
			String evSido = "", evSigungu = "", evAddr = "";
			for (TourVO v : spots) {
				if (Area.isValid(v.getSidoCode())) {
					evSido = v.getSidoCode();
					evSigungu = v.getSigunguCode() == null ? "" : v.getSigunguCode();
					evAddr = v.getAddr();
					break;
				}
			}
			final String fSido = evSido, fSigungu = evSigungu, fAddr = evAddr;
			final LocalDate date = Area.dayOf(fDay);
			CompletableFuture<RegionEvidence> fEv = ApiClient.asyncOuter(
					() -> RegionEvidence.load(key, fSido, fSigungu, fAddr, fLat, fLon, date));
			// 5판: 이 자리가 어느 시 · 군 · 구인지 이름으로 (「이 지역 하루 코스」 링크에 쓴다. 시군구 목록은 24시간 캐시)
			CompletableFuture<List<String[]>> fGu = Area.isValid(fSido) ? ApiClient.async(() -> tour.sigunguList(fSido)) : null;

			boolean midDay = false;
			String midNote = "";
			if (fDay <= 2) {
				try { weather = ApiClient.await(fWeather); } catch (ApiException e) { weatherError = e.getMessage(); }
			} else {
				DayForecastVO dayFc = new ForecastStitcher(key).tenDays(fLat, fLon, fSido, fSigungu).getDay(fDay);
				weather = dayFc.getWeather();
				midDay = DayForecastVO.MID.equals(dayFc.getSource());
				midNote = dayFc.getNote();
				if (weather == null) weatherError = dayFc.getNote();
			}
			if (tourFailed && weather == null) {
				write(response, 502, error("API", (tourErr != null ? tourErr.getMessage() : weatherError)));
				return;
			}
			RegionEvidence ev = null;
			try {
				ev = fEv.join();
			} catch (Exception e) {
				e.printStackTrace();
			}
			int airGrade = ev != null && ev.airView(fDay) != null ? ev.airView(fDay).getGrade() : 0;

			// ④ 날씨(+ 미세먼지) → 추천 → 관광지마다 맞는지
			RecommendVO rec = weather == null ? null : Recommender.recommend(weather, airGrade);
			Set<String> inCourse = CourseStore.ids(session);
			JsonArray list = new JsonArray();
			for (TourVO s : spots) {
				if (rec != null) {
					s.setRecMode(rec.getMode());
					s.setFit(Recommender.fits(s, rec));
				}
				JsonObject p = new JsonObject();
				p.addProperty("id", s.getContentId());
				p.addProperty("typeId", s.getContentTypeId());
				p.addProperty("type", s.getTypeName());
				p.addProperty("title", s.getTitle());
				p.addProperty("addr", s.getAddr());
				p.addProperty("image", s.getImage());
				p.addProperty("lat", s.getLat());
				p.addProperty("lon", s.getLon());
				p.addProperty("distM", s.getDistM());
				p.addProperty("distKm", s.getDistKm());
				p.addProperty("indoor", s.isIndoor());
				p.addProperty("fit", s.isFit());
				p.addProperty("url", MapJson.detailUrl(s.getContentId(), s.getContentTypeId(), fDay, "", ""));
				p.addProperty("inCourse", inCourse.contains(s.getContentId()));
				// 여행 지수 (이 곳 이름이 집중률 목록과 맞으면 이 곳 붐빔, 아니면 시군구 평균)
				Double crowd = ev == null ? null : ev.crowdOfSpot(s.getTitle(), fDay);
				s.setCrowdRate(crowd);
				s.setScore(TravelScore.score(ev, fDay, weather, s.isIndoor(), crowd));
				MapJson.addScore(p, s.getScore());
				p.addProperty("crowd", s.getCrowdText());
				list.add(p);
			}

			JsonObject o = new JsonObject();
			o.addProperty("ok", true);
			o.addProperty("code", "OK");
			o.addProperty("message", "");
			o.addProperty("lat", lat);
			o.addProperty("lon", lon);
			o.addProperty("day", day);
			o.addProperty("dayLabel", Area.dayOptions().get(day)[1]);
			o.addProperty("type", type);
			int[] grid = GridConverter.toGrid(lat, lon);
			JsonObject g = new JsonObject();
			g.addProperty("nx", grid[0]);
			g.addProperty("ny", grid[1]);
			o.add("grid", g);
			o.add("weather", weather == null ? null : MapJson.weather(weather));
			o.addProperty("weatherError", weatherError);
			o.add("rec", rec == null ? null : MapJson.rec(rec));
			// 2판: 이 자리의 여행 지수 · 새 API 자료 요약 · 중기예보 안내 (자료가 없는 칸은 null 이고 이유가 따로 있다)
			o.add("score", MapJson.score(TravelScore.score(ev, fDay, weather, false, null)));
			o.add("evidence", ev == null ? null : MapJson.evidence(ev, fDay, ev.getSun()));
			o.addProperty("midDay", midDay);
			o.addProperty("midNote", midDay ? midNote : "");
			o.add("spots", list);
			o.addProperty("count", list.size());
			o.addProperty("supplemented", supplemented);
			o.addProperty("spotsError", tourFailed && tourErr != null ? tourErr.getMessage() : "");
			boolean empty = list.size() == 0;
			o.addProperty("empty", empty);
			o.addProperty("emptyMessage", !empty ? "" : tourFailed
					? "주변 관광지를 불러오지 못했어요. 잠시 뒤 다시 눌러 보세요."
					: "이 근처엔 등록된 관광지가 적어요. 지도에서 다른 곳을 눌러 보세요.");
			o.addProperty("courseCount", CourseStore.count(session));

			// 5판: 여기서 출발하는 추천 동선 (이미 받은 목록만 쓴다 · 추가 호출 없음)
			int sunsetMin = ev != null && ev.getSun() != null && ev.getSun().isHasTimes() ? ev.getSun().getSunsetMinutes() : -1;
			NearbyRoute.Result route = NearbyRoute.plan(spots, lat, lon, fDay, LocalTime.now(ZoneId.of("Asia/Seoul")), sunsetMin);
			if (route.isAvailable()) { // 관광지 사이만 실제 길로 (누른 곳 · 내 위치는 바깥 길 찾기 서버로 보내지 않는다)
				List<double[]> pts = new ArrayList<>();
				for (NearbyRoute.Stop st : route.getStops()) pts.add(new double[] { st.getSpot().getLat(), st.getSpot().getLon() });
				NearbyRoute.applyRoad(route, RoadRoute.through(pts));
			}
			o.add("route", MapJson.route(route, fDay, "", ""));
			// 5판: 이 자리의 시 · 도 / 시 · 군 · 구 (모르면 null)
			if (Area.isValid(fSido)) {
				String guName = "";
				if (fGu != null) {
					try {
						for (String[] gu : fGu.get(3, TimeUnit.SECONDS)) if (gu[0].equals(fSigungu)) guName = gu[1];
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (Exception e) { /* 이름 없이 시 · 도만 */ }
				}
				JsonObject region = new JsonObject();
				region.addProperty("area", fSido);
				region.addProperty("sigungu", guName.isEmpty() ? "" : fSigungu);
				region.addProperty("name", Area.name(fSido) + (guName.isEmpty() ? "" : " " + guName));
				o.add("region", region);
			}
			write(response, 200, o);
		} catch (ApiException e) {
			write(response, 502, error("API", e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(response, 500, error("SERVER", "처리 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요."));
		}
	}

	// 가까운 곳의 지역(시도 · 시군구) 관광지 · 문화시설 목록에서 반경 5km 안의 곳을 더한다
	// 지역은 이미 찾은 곳의 법정동 코드에서, 하나도 없으면 타입 없이 가장 가까운 3곳에서 알아낸다
	private List<TourVO> supplement(TourDAO tour, double lat, double lon, List<TourVO> found, boolean want12, boolean want14) {
		List<TourVO> extra = new ArrayList<>();
		try {
			String sido = "", gu = "";
			for (TourVO v : found) {
				if (Area.isValid(v.getSidoCode())) {
					sido = v.getSidoCode();
					gu = v.getSigunguCode();
					break;
				}
			}
			if (sido.isEmpty()) {
				for (TourVO v : tour.nearby(lat, lon, RADIUS_M, "", 3)) {
					if (Area.isValid(v.getSidoCode())) {
						sido = v.getSidoCode();
						gu = v.getSigunguCode();
						break;
					}
				}
			}
			if (sido.isEmpty()) return extra; // 어느 지역인지 모르면 보충 못 함
			final String s = sido, g = gu == null ? "" : gu;
			CompletableFuture<List<TourVO>> a12 = want12 ? ApiClient.async(() -> tour.areaList(s, g, "12", 16)) : null;
			CompletableFuture<List<TourVO>> a14 = want14 ? ApiClient.async(() -> tour.areaList(s, g, "14", 8)) : null;
			List<TourVO> pool = new ArrayList<>();
			if (a12 != null) { try { pool.addAll(ApiClient.await(a12)); } catch (ApiException e) { /* 보충이라 실패해도 그대로 */ } }
			if (a14 != null) { try { pool.addAll(ApiClient.await(a14)); } catch (ApiException e) { /* 위와 같음 */ } }
			extra = TourDAO.withinRadius(pool, lat, lon, RADIUS_M, found, "");
		} catch (ApiException e) {
			// 보충 실패는 무시하고 있는 것만 보여 준다
		}
		return extra;
	}

	// 오류 답: {"ok":false,"code":"...","message":"..."}
	private static JsonObject error(String code, String message) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("code", code);
		o.addProperty("message", message);
		return o;
	}

	private void write(HttpServletResponse response, int status, JsonObject o) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json; charset=UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.getWriter().write(MapJson.toText(o));
	}
}
