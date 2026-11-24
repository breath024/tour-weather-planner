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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.inha.model.ApiClient;
import com.inha.model.ApiException;
import com.inha.model.ApiKey;
import com.inha.model.Area;
import com.inha.model.CategoryFilter;
import com.inha.model.CoursePlanner;
import com.inha.model.CourseStore;
import com.inha.model.CrowdVO;
import com.inha.model.DayForecastVO;
import com.inha.model.DayPlanVO;
import com.inha.model.DayScoreVO;
import com.inha.model.ForecastStitcher;
import com.inha.model.GridWeather;
import com.inha.model.MapJson;
import com.inha.model.PlanBlockVO;
import com.inha.model.RecommendVO;
import com.inha.model.Recommender;
import com.inha.model.RegionEvidence;
import com.inha.model.RoadRoute;
import com.inha.model.TenDayVO;
import com.inha.model.TourDAO;
import com.inha.model.TourVO;
import com.inha.model.TravelScore;
import com.inha.model.WeatherDAO;
import com.inha.model.WeatherVO;

// 검색 → 결과 화면
// 융합 흐름: ① 관광공사 관광지 목록 → ② 관광지 좌표로 기상청 날씨(단기 + 중기, 0~10일)
//           → ③ 에어코리아 · 자외선 · 천문연 · 집중률 · 기상특보 → ④ 여행 지수 + 날씨로 실내/실외 추천 필터
@WebServlet("/plan")
public class PlanController extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int FESTIVAL_ROWS = 100;  // 축제 · 행사 목록 요청 개수 (오늘 ~ 10일 뒤 기간 전체를 한 번에 받는다)
	private static final int FESTIVAL_SHOW = 12;   // 화면에 보여 줄 행사 수 (넘으면 「외 N곳」)
	private static final int FESTIVAL_GRIDS = 8;   // 행사 날씨를 조회할 격자 수 상한 (곁가지라 기상청 호출을 아낀다)

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");

		String area = request.getParameter("area");
		String sigungu = request.getParameter("sigungu");
		int day = Area.parseDay(request.getParameter("day"));
		String dayProblem = Area.dayProblem(request.getParameter("day")); // 잘못된 날짜는 조용히 오늘로 바꾸지 않고 문구로 알린다
		if (!dayProblem.isEmpty()) request.setAttribute("dayNotice", dayProblem);
		if (sigungu == null || !sigungu.matches("\\d{1,5}")) sigungu = "";
		String keyword = cleanKeyword(request.getParameter("keyword"));

		// 검색 폼에 필요한 값
		request.setAttribute("areas", Area.sidoMap());
		request.setAttribute("days", Area.dayOptions());
		request.setAttribute("selArea", Area.isValid(area) ? area : "");
		request.setAttribute("selSigungu", sigungu);
		request.setAttribute("selDay", day);
		request.setAttribute("keyword", keyword);
		request.setAttribute("keywordSearch", !keyword.isEmpty());
		request.setAttribute("mapJson", "{\"center\":null,\"spots\":[],\"course\":[],\"courseKm\":0}"); // 결과가 없어도 항상 있음
		// 첫 화면 「지금 내 주변」: 내 위치를 못 받으면 보여 줄 기본 자리(인천 개항장 일대)
		request.setAttribute("homeLat", Area.HOME_LAT);
		request.setAttribute("homeLon", Area.HOME_LON);
		request.setAttribute("homeName", Area.HOME_NAME);
		request.setAttribute("homeArea", Area.HOME_AREA);
		request.setAttribute("homeSigungu", Area.HOME_SIGUNGU);

		boolean hasArea = area != null && !area.isEmpty();
		if (hasArea && !Area.isValid(area)) {
			request.setAttribute("error", "지역을 목록에서 다시 골라 주세요.");
		} else if (hasArea || !keyword.isEmpty()) {
			// 지역을 골랐거나 키워드를 넣었으면 검색 (키워드만 있으면 전국에서 찾는다)
			search(request, hasArea ? area : "", hasArea ? sigungu : "", day, keyword, request.getParameter("cat"));
		}
		request.getRequestDispatcher("/WEB-INF/views/plan.jsp").forward(request, response);
	}

	private static LocalTime nowKst() {
		return LocalTime.now(ZoneId.of("Asia/Seoul"));
	}

	// 하루 코스 칸 사이 실제 길: 관광지 좌표만 보낸다. 못 받으면 아무것도 안 바꾼다
	static void attachRoad(DayPlanVO plan) {
		List<double[]> pts = new ArrayList<>();
		for (PlanBlockVO b : plan.getBlocks()) {
			if (b.getSpot() == null || !b.getSpot().isHasLocation()) return;
			pts.add(new double[] { b.getSpot().getLat(), b.getSpot().getLon() });
		}
		RoadRoute.Result rr = RoadRoute.through(pts);
		if (rr == null) return;
		for (int i = 0; i + 1 < plan.getBlocks().size(); i++) plan.getBlocks().get(i).setRoad(rr.getLegs().get(i));
		plan.setRoad(rr);
	}

	// 키워드: 앞뒤 공백 · 제어문자 제거, 연속 공백 하나로, 30자까지
	private static String cleanKeyword(String s) {
		if (s == null) return "";
		// 글자 · 숫자 · 공백 · 하이픈 · 점 · 가운뎃점 · 괄호만 남긴다 (<script> 같은 낱말은 API 가 오류를 내서)
		s = s.replaceAll("[^\\p{L}\\p{N}\\s\\-.·()]", " ").replaceAll("\\s+", " ").trim();
		return s.length() > 30 ? s.substring(0, 30).trim() : s;
	}

	// 시군구 고르기 목록 · 지역 이름(예: 경북 경주시)을 request 에 담는다. 두 번 불려도 한 번만 한다
	private String applyRegion(HttpServletRequest request, CompletableFuture<List<String[]>> fSigungu, boolean hasArea, String area, String sigungu) {
		Object done = request.getAttribute("regionName");
		if (done != null) return (String) done;
		List<String[]> sigunguList;
		try {
			sigunguList = fSigungu.get(3, TimeUnit.SECONDS); // 3초 넘게 안 오면 포기하고 빈 목록 (실패해도 빈 목록이 온다)
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			sigunguList = new ArrayList<>();
		} catch (Exception e) { // 시간 초과 · 실패
			sigunguList = new ArrayList<>();
		}
		request.setAttribute("sigunguList", sigunguList);
		String regionName = hasArea ? Area.name(area) : "전국";
		for (String[] s : sigunguList) {
			if (s[0].equals(sigungu)) regionName += " " + s[1];
		}
		request.setAttribute("regionName", regionName);
		return regionName;
	}

	// 목록에서 법정동 시도 코드를 아는 첫 관광지의 시도 코드 (전국 키워드 검색에서 새 API 자료의 지역을 정할 때)
	private static String firstSido(List<TourVO> spots) {
		for (TourVO v : spots) {
			if (Area.isValid(v.getSidoCode())) return v.getSidoCode();
		}
		return "";
	}

	// 고른 날의 새 API 자료를 화면용 속성으로 풀어 담는다 (자료가 없으면 이유 문구가 들어간다)
	static void putEvidence(HttpServletRequest request, RegionEvidence ev, int day) {
		if (ev == null) {
			request.setAttribute("airMissing", "미세먼지 자료를 못 불러왔어요.");
			request.setAttribute("uvMissing", "자외선 자료를 못 불러왔어요.");
			request.setAttribute("crowdMissing", "붐빔 자료를 못 불러왔어요.");
			request.setAttribute("warnError", "기상특보를 못 불러왔어요.");
			return;
		}
		request.setAttribute("ev", ev);
		RegionEvidence.AirView air = ev.airView(day);
		if (air != null) request.setAttribute("airView", air);
		else request.setAttribute("airMissing", ev.airMissingReason(day));
		RegionEvidence.UvView uv = ev.uvView(day);
		if (uv != null) request.setAttribute("uvView", uv);
		else request.setAttribute("uvMissing", ev.uvMissingReason(day));
		if (ev.getSun() != null && ev.getSun().isHasTimes()) request.setAttribute("sun", ev.getSun());
		else request.setAttribute("sunError", ev.getSunError().isEmpty() ? "해 뜨고 지는 시각을 못 구했어요." : ev.getSunError());
		Double avg = ev.crowdAverage(day);
		if (avg != null) {
			request.setAttribute("crowdAvg", avg);
			request.setAttribute("crowdAvgText", Math.round(avg) + "% (" + CrowdVO.levelText(avg) + ")"); // 칸 이름이 「붐빔」이라 앞에 또 붙이지 않는다
		} else {
			request.setAttribute("crowdMissing", ev.crowdMissingReason());
			request.setAttribute("crowdNeedsSigungu", ev.crowdNeedsSigungu()); // 시군구를 고르면 되는 경우만 그렇게 안내 (광주·전남처럼 자료 자체가 없으면 안 함)
		}
		if (ev.getWarning() != null && ev.getWarning().getTmFc().matches("\\d{12}")) {
			String t = ev.getWarning().getTmFc(); // 202611181600 → 11/18 16:00
			request.setAttribute("warnIssued", t.substring(4, 6) + "/" + t.substring(6, 8) + " " + t.substring(8, 10) + ":" + t.substring(10, 12));
		}
		request.setAttribute("warnCurrent", ev.hereCurrent());   // 고른 시군구에 해당하는 특보 (시군구를 안 골랐으면 시도 전체)
		request.setAttribute("warnPrelim", ev.herePrelimOn(day)); // 그 날 발효 예정인 예비특보만
		request.setAttribute("warnFar", day >= 2);                // 모레부터는 「지금 발효 중인 특보」일 뿐 그 날 특보는 아직 모른다 → 배너가 차분한 안내로 바뀐다
		request.setAttribute("warnElsewhere", ev.elsewhereCount()); // 같은 시도의 다른 지역 특보 수
		if (!ev.getWarningError().isEmpty()) request.setAttribute("warnError", ev.getWarningError());
	}

	// 이 날 열리는 축제 · 행사: 목록 + 그 자리 날씨 + 야외 행사에 비 예보면 경고.
	// 날씨는 행사 좌표를 기상청 격자로 묶어 격자마다 한 번만(최대 FESTIVAL_GRIDS 개, 관광지와 겹치는 격자는 캐시라 호출 0).
	// 중기예보 날은 지역 단위 날씨 하나를 모두에게 붙인다. 0건이면 한 줄 문구, 실패해도 예외를 던지지 않는다
	private List<TourVO> festivalSection(HttpServletRequest request, CompletableFuture<List<TourVO>> f, double[] center,
			WeatherDAO weatherDao, int day, boolean midDay, DayForecastVO dayFc) {
		List<TourVO> events = new ArrayList<>();
		if (f == null) return events; // 지역을 안 골랐으면 행사 칸 자체가 없다
		try {
			events = ApiClient.await(f);
		} catch (ApiException e) {
			request.setAttribute("festivalMsg", "축제 · 행사 정보를 불러오지 못했어요. (" + e.getMessage() + ")");
			return new ArrayList<>();
		}
		request.setAttribute("festivalCount", events.size());
		if (events.isEmpty()) {
			request.setAttribute("festivalMsg", "이날 이 지역에서 열리는 축제 · 행사는 없어요.");
			return events;
		}
		if (events.size() > FESTIVAL_SHOW) {
			request.setAttribute("festivalMore", events.size() - FESTIVAL_SHOW);
			events = new ArrayList<>(events.subList(0, FESTIVAL_SHOW));
		}
		try {
			if (midDay) {
				for (TourVO e : events) e.setWeather(dayFc.getWeather());
			} else if (dayFc.isAvailable()) {
				GridWeather.load(events, center, weatherDao, day, FESTIVAL_GRIDS);
			}
		} catch (Exception e) {
			System.out.println("[TourWeather] 행사 날씨 조회 실패: " + e.getMessage());
		}
		int warnCount = 0;
		for (TourVO e : events) {
			CourseStore.rememberEventPeriod(request.getSession(), e.getContentId(), e.getEventStart(), e.getEventEnd());   // 담을 때 붙일 행사 기간
			WeatherVO w = e.getWeather();
			if (e.isIndoor() || w == null) continue;
			if (w.isRainy() || w.getMaxPop() >= 40) {
				String what = w.isRainy() ? (w.getRainType().isEmpty() ? "비" : w.getRainType()) + " 예보가 있어요" : "강수확률이 " + w.getMaxPop() + "%예요";
				// 야외인지 확실할 때만 「야외 행사」라고 쓴다. 모르면 조건으로 쓴다
				e.setEventWarn((e.isPlaceUnknown() ? "야외에서 열린다면 " : "야외 행사인데 ") + what + ". 우산이나 우비를 챙기세요.");
				warnCount++;
			}
		}
		request.setAttribute("festivalWarnCount", warnCount);
		request.setAttribute("festivals", events);
		return events;
	}

	private void search(HttpServletRequest request, String area, String sigungu, int day, String keyword, String rawCat) {
		try {
			String key = ApiKey.load(getServletContext());
			TourDAO tourDao = new TourDAO(key);
			WeatherDAO weatherDao = new WeatherDAO(key);
			boolean hasArea = !area.isEmpty();
			boolean byKeyword = !keyword.isEmpty();

			// ① 독립 호출을 동시에: 시군구 목록 · 관광지(12) · 문화시설(14)
			//    시군구 목록은 꼭 필요한 건 아니라서 맨 마지막에 기다린다 (느려도 날씨 조회를 붙잡지 않게)
			CompletableFuture<List<String[]>> fSigungu = hasArea
					? ApiClient.async(() -> tourDao.sigunguList(area))
					: CompletableFuture.completedFuture(new ArrayList<String[]>());
			CompletableFuture<List<TourVO>> f12 = ApiClient.async(() -> byKeyword
					? tourDao.keywordList(keyword, area, sigungu, "12", 16)
					: tourDao.areaList(area, sigungu, "12", 16));
			CompletableFuture<List<TourVO>> f14 = ApiClient.async(() -> byKeyword
					? tourDao.keywordList(keyword, area, sigungu, "14", 8)
					: tourDao.areaList(area, sigungu, "14", 8));

			// 곁가지(실패해도 화면은 뜬다): 분류 이름표(24시간 캐시) · 이 날 열리는 축제 · 행사(지역을 골랐을 때만)
			CompletableFuture<Map<String, String>> fNames = ApiClient.async(() -> tourDao.lclsNames());
			final LocalDate festDate = Area.dayOf(day);
			CompletableFuture<List<TourVO>> fFest = hasArea
					? ApiClient.async(() -> tourDao.festivals(area, sigungu, festDate, FESTIVAL_ROWS))
					: null;

			try {
				// 하나가 실패해도 나머지는 보여 준다
				List<TourVO> spots = new ArrayList<>();
				ApiException err12 = null, err14 = null;
				try { spots.addAll(ApiClient.await(f12)); } catch (ApiException e) { err12 = e; }
				try { spots.addAll(ApiClient.await(f14)); } catch (ApiException e) { err14 = e; }
				if (err12 != null && err14 != null) {
					request.setAttribute("error", err12.getMessage());
					return;
				}
				if (err12 != null) { request.setAttribute("partialError", "관광지 목록을 못 불러와서 문화시설만 나와요."); System.err.println("[TourWeather] 관광지 목록 실패: " + err12.getMessage()); }
				if (err14 != null) { request.setAttribute("partialError", "문화시설 목록을 못 불러와서 관광지만 나와요."); System.err.println("[TourWeather] 문화시설 목록 실패: " + err14.getMessage()); }

				request.setAttribute("total", spots.size());
				if (spots.isEmpty()) {
					String regionName = applyRegion(request, fSigungu, hasArea, area, sigungu); // 시군구 이름이 필요한 문구라 여기서는 기다린다
					if (byKeyword) {
						request.setAttribute("noKeywordResult", true);
						request.setAttribute("error", hasArea
							? regionName + "에서 '" + keyword + "' 로 찾은 관광지가 없어요. 낱말을 줄이거나 지역 전체 보기를 눌러 보세요."
							: "전국에서 '" + keyword + "' 로 찾은 관광지가 없어요. 낱말을 줄이거나 지역을 골라 보세요.");
					} else {
						request.setAttribute("error", regionName + "에서 찾은 관광지가 없어요. 다른 지역을 골라 보세요."
								+ (sigungu.matches("\\d{2}0") ? " 구가 나뉜 시(전주 · 수원 · 성남 …)라면 '○○시 ○○구'를 골라 주세요." : ""));
					}
					return;
				}

				// 분류(대분류) 이름 + 칩 + 고른 분류 (?cat=). 하루 코스 · 날씨는 거르기 전 전체 목록으로 계산하고, 카드 · 지도 마커만 거른다
				Map<String, String> lclsNames = null;
				try { lclsNames = ApiClient.await(fNames); } catch (ApiException e) { /* 이름표는 없어도 된다 */ }
				CategoryFilter.applyNames(spots, lclsNames);
				String selCat = CategoryFilter.validSel(rawCat, CategoryFilter.chips(spots, ""));
				if (rawCat != null && !rawCat.isEmpty() && selCat.isEmpty()) {
					request.setAttribute("catNotice", "고른 분류가 이 결과에 없어서 전체 목록이에요.");
				}
				request.setAttribute("selCat", selCat);
				List<CategoryFilter.Chip> catChips = CategoryFilter.chips(spots, selCat);
				if (catChips.size() > 2) request.setAttribute("catChips", catChips); // 전체 + 한 가지뿐이면 거를 게 없다
				List<TourVO> shownSpots = CategoryFilter.filter(spots, selCat);
				request.setAttribute("shownCount", shownSpots.size());
				if (!selCat.isEmpty()) request.setAttribute("catNote", "분류를 골라도 하루 코스 · 날씨 · 여행 지수는 거르기 전 전체 " + spots.size() + "곳 기준이에요.");

				// ② 관광지 좌표의 가운데 → API ② 기상청 날씨
				double[] center = Recommender.center(spots);
				if (!hasArea && center != null) {
					// 전국 키워드 검색이면 중앙값이 뜻이 없어서 첫 결과의 위치를 기준으로 한다
					for (TourVO v : spots) {
						if (v.isHasLocation()) {
							center = new double[] { v.getLat(), v.getLon() };
							break;
						}
					}
					request.setAttribute("centerNote", "검색 첫 결과 위치 기준");
				}
				if (center == null) {
					request.setAttribute("weatherError", "관광지 좌표가 없어 날씨를 조회하지 못했어요.");
					request.setAttribute("others", shownSpots);
					return;
				}

				// ②-1 새 API 자료(미세먼지 · 자외선 · 일출몰 · 붐빔 · 특보)는 따로 시작해서 아래 날씨 조회와 겹쳐 기다린다.
				//      RegionEvidence.load 는 안에서 ApiClient 풀 작업을 기다리므로 풀 밖(기본 풀)에서 돌린다
				final String evSido = hasArea ? area : firstSido(spots);
				final String evSigungu = hasArea ? sigungu : "";
				final String evAddr = spots.get(0).getAddr();
				final double[] ctr = center;
				final LocalDate date = Area.dayOf(day);
				CompletableFuture<RegionEvidence> fEv = ApiClient.asyncOuter(
						() -> RegionEvidence.load(key, evSido, evSigungu, evAddr, ctr[0], ctr[1], date));

				// ②-2 단기 + 중기를 이어 붙인 0~10일 예보 (날씨 달력 · 고른 날이 중기예보인지 가리는 데 쓴다)
				TenDayVO ten = new ForecastStitcher(key).tenDays(center[0], center[1], evSido, evSigungu);
				DayForecastVO dayFc = ten.getDay(day);
				boolean midDay = DayForecastVO.MID.equals(dayFc.getSource());
				request.setAttribute("dayFc", dayFc);
				request.setAttribute("midDay", midDay);
				request.setAttribute("midNote", dayFc.getNote());

				// ②-3 관광지마다 자기 격자 날씨를 붙인다: 같은 격자는 한 번만, 격자 최대 16개를 동시에 조회 (가운데 날씨 포함)
				//      중기예보 날은 지역 단위 날씨 하나라서 모든 관광지에 같은 값을 붙인다
				WeatherVO weather;
				String weatherErr = null;
				if (midDay) {
					weather = dayFc.getWeather();
					for (TourVO v : spots) {
						v.setWeather(weather);
						v.setWeatherApprox(false);
					}
					request.setAttribute("gridCount", 0);
					request.setAttribute("approxCount", 0);
				} else if (!dayFc.isAvailable()) {
					weather = null;
					weatherErr = dayFc.getNote();
				} else {
					GridWeather gw = GridWeather.load(spots, center, weatherDao, day);
					request.setAttribute("gridCount", gw.getGridCount());
					request.setAttribute("approxCount", gw.getApproxCount());
					weather = gw.getCenter();
					if (weather == null) weatherErr = gw.getCenterError() == null ? "날씨를 불러오지 못했어요." : gw.getCenterError().getMessage();
				}

				// 새 API 자료를 모은다 (통째로 실패하면 null → 점수 칩이 모두 "자료 없음")
				RegionEvidence ev = null;
				try {
					ev = fEv.join();
				} catch (Exception e) {
					e.printStackTrace();
				}
				putEvidence(request, ev, day);
				int airGrade = 0;
				if (ev != null && ev.airView(day) != null) airGrade = ev.airView(day).getGrade();

				// 이 날 열리는 축제 · 행사 (목록 + 그 자리 날씨 + 야외 비 경고). 실패해도 아래는 계속 진행
				List<TourVO> events = festivalSection(request, fFest, center, weatherDao, day, midDay, dayFc);

				// 여행 지수: 지역 점수 + 날씨 달력(가장 좋은 날 띠)
				request.setAttribute("regionScore", TravelScore.score(ev, day, weather, false, null));
				List<DayScoreVO> strip = TravelScore.dayStrip(ten, ev, day);
				request.setAttribute("dayStrip", strip);
				for (DayScoreVO d : strip) {
					if (d.isBest()) request.setAttribute("bestDay", d);
				}
				request.setAttribute("midTaCity", ten.getMidTaCity()); // 4일 뒤부터의 기온을 가져온 도시 (화면에 밝힌다)
				request.setAttribute("tenShortError", ten.getShortError());
				request.setAttribute("tenMidError", ten.getMidError());

				// 관광지마다 점수 + 붐빔 배지 (집중률 목록의 이름과 맞는 관광지만)
				int crowdMatched = 0;
				for (TourVO v : spots) {
					Double crowd = ev == null ? null : ev.crowdOfSpot(v.getTitle(), day);
					v.setCrowdRate(crowd);
					if (crowd != null) crowdMatched++;
					WeatherVO own = v.getWeather() != null ? v.getWeather() : weather;
					v.setScore(TravelScore.score(ev, day, own, v.isIndoor(), crowd));
				}
				request.setAttribute("crowdMatched", crowdMatched);

				if (weather == null) {
					// 요약 날씨가 없으면 관광지 목록만 보여 준다 (점수는 남은 근거로)
					request.setAttribute("weatherError", weatherErr);
					request.setAttribute("others", shownSpots);
					request.setAttribute("mapJson", MapJson.plan(shownSpots, null, center, day, area, sigungu, events, selCat, dayFc, nowKst().getHour()));
					return;
				}

				// ③ 날씨(+ 미세먼지) → 추천 방향. 요약 카드는 가운데 날씨, 관광지별 추천은 그 관광지 자기 자리 날씨로 판단
				RecommendVO rec = Recommender.recommend(weather, airGrade);
				List<TourVO> recommended = new ArrayList<>();
				List<TourVO> others = new ArrayList<>();
				for (TourVO v : spots) {
					WeatherVO w = v.getWeather() != null ? v.getWeather() : weather;
					RecommendVO own = Recommender.recommend(w, airGrade);
					v.setRecMode(own.getMode());
					v.setFit(Recommender.fits(v, own));
					v.setTip(Recommender.spotTip(v, w));
					(v.isFit() ? recommended : others).add(v);
				}
				if (recommended.isEmpty()) {
					request.setAttribute("noMatch", true);
					recommended = spots;
					others = new ArrayList<>();
				}
				recommended = CategoryFilter.filter(recommended, selCat); // 카드만 거른다 (코스 · 날씨 · 점수는 위에서 전체로 끝났다)
				others = CategoryFilter.filter(others, selCat);

				// ④ 날씨 맞춤 하루 코스 (지역을 골랐을 때만. 전국 키워드 검색은 흩어져 있어서 코스를 안 짠다)
				//    해 지는 시각을 알면 실외 칸은 일몰 전에 끝낸다. 중기예보 날은 오전 · 오후 2칸
				DayPlanVO dayPlan;
				if (hasArea) {
					boolean sunOk = ev != null && ev.getSun() != null && ev.getSun().isHasTimes();
					dayPlan = CoursePlanner.plan(spots, center, weather, day, LocalTime.now(ZoneId.of("Asia/Seoul")),
							dayFc, sunOk ? ev.getSun().getSunset() : "");
					if (sunOk) dayPlan.setSunrise(ev.getSun().getSunrise());
				} else {
					dayPlan = new DayPlanVO();
					dayPlan.setMessage("지역을 고르면 그 지역 안에서 하루 코스를 짜요.");
					dayPlan.setNote("");
				}
				// 보여 준 코스의 번호를 세션에 기억: '이 코스 통째로 담기'는 이 번호만 받는다
				if (dayPlan.isAvailable()) {
					attachRoad(dayPlan); // 칸 사이를 실제 길로 (오픈스트리트맵 길 찾기 · 못 받으면 직선 그대로)
					CourseStore.rememberPlanIds(request.getSession(), dayPlan.getIds());
					CourseStore.rememberPlanSlots(request.getSession(), dayPlan); // 칸 시간도 기억 → 담으면 달력(.ics) 시간이 된다
				}

				request.setAttribute("weather", weather);
				request.setAttribute("rec", rec);
				request.setAttribute("recommended", recommended);
				request.setAttribute("others", others);
				commonWeather(request, recommended, others); // 카드마다 같은 날씨 반복 안 하게
				request.setAttribute("dayPlan", dayPlan);
				// 마커는 거른 목록, 코스 경로는 전체 기준. 시간 슬라이더 자료("time")는 마커에 붙은 격자 날씨를 그대로 옮긴다(추가 호출 없음)
				request.setAttribute("mapJson", MapJson.plan(shownSpots, dayPlan, center, day, area, sigungu, events, selCat, dayFc, nowKst().getHour()));
			} finally {
				applyRegion(request, fSigungu, hasArea, area, sigungu); // 어떤 길로 끝나도 시군구 목록 · 지역 이름은 채운다
			}

		} catch (ApiException e) {
			request.setAttribute("error", e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			request.setAttribute("error", "처리 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요.");
		}
	}

	// 절반 넘는 곳 날씨가 같으면 그 날씨를 목록 위에 한번만 (카드엔 다른 곳만)
	static void commonWeather(HttpServletRequest request, List<TourVO> a, List<TourVO> b) {
		Map<String, Integer> cnt = new java.util.HashMap<>();
		Map<String, TourVO> ref = new java.util.HashMap<>();
		int n = 0;
		for (List<TourVO> list : List.of(a, b)) {
			for (TourVO v : list) {
				String k = v.getWxKey();
				if (k.isEmpty()) continue;
				n++;
				cnt.merge(k, 1, Integer::sum);
				ref.putIfAbsent(k, v);
			}
		}
		String best = null;
		for (Map.Entry<String, Integer> e : cnt.entrySet()) {
			if (best == null || e.getValue() > cnt.get(best)) best = e.getKey();
		}
		if (best == null || n < 2 || cnt.get(best) * 2 <= n) return;
		request.setAttribute("wxCommonKey", best);
		request.setAttribute("wxCommonSpot", ref.get(best));
		request.setAttribute("wxAllSame", cnt.get(best) == n);
	}
}
