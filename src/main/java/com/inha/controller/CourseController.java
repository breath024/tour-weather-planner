package com.inha.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inha.model.ApiClient;
import com.inha.model.ApiException;
import com.inha.model.ApiKey;
import com.inha.model.Area;
import com.inha.model.CourseItem;
import com.inha.model.CourseStopVO;
import com.inha.model.DayForecastVO;
import com.inha.model.ForecastStitcher;
import com.inha.model.MapJson;
import com.inha.model.RegionCodes;
import com.inha.model.RegionEvidence;
import com.inha.model.TravelScore;
import com.inha.model.WarningItem;
import com.inha.model.CourseStore;
import com.inha.model.RateLimiter;
import com.inha.model.ShareLink;
import com.inha.model.SharedCourse;
import com.inha.model.GridConverter;
import com.inha.model.IcsBuilder;
import com.inha.model.Geo;
import com.inha.model.TourDAO;
import com.inha.model.TourVO;
import com.inha.model.WeatherDAO;
import com.inha.model.WeatherVO;

// 내 코스: GET = 내 코스 화면, POST = 담기 · 빼기 · 순서 · 비우기 (처리 뒤 redirect, PRG)
// 자바스크립트 fetch 로 보내면(Accept: application/json 또는 X-Requested-With) JSON 으로 답한다
@WebServlet("/course")
public class CourseController extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Gson GSON = new Gson();   // 기본값 그대로: < > & 를 < 처럼 바꿔 줘서 script 태그 안에 넣어도 안전
	// 처리 뒤에 돌아갈 곳: 이 앱의 화면 주소만 허용 (바깥 주소로 튕기는 것을 막음)
	private static final Pattern NEXT = Pattern.compile("^(plan|detail|course|explore)(\\?[A-Za-z0-9%=&_.~+,*-]{0,300})?(#[A-Za-z0-9_-]{1,60})?$");

	// ---------------- GET: 내 코스 화면 ----------------
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		int day = Area.parseDay(request.getParameter("day"));
		// 공유 링크의 보낸 사람이 고른 날짜(date): 오늘 기준 며칠 뒤인지로 바꿔 쓴다. 지났거나 너무 멀면 오늘 기준으로 보여 주고 알린다
		String dateNotice = "";
		if (request.getParameter("share") != null && ShareLink.parseDate(request.getParameter("date")) != null) {
			java.time.LocalDate sent = ShareLink.parseDate(request.getParameter("date"));
			long off = java.time.temporal.ChronoUnit.DAYS.between(Area.dayOf(0), sent);
			String label = sent.getMonthValue() + "/" + sent.getDayOfMonth();
			if (off >= 0 && off <= Area.MAX_DAY) {
				day = (int) off;
			} else {
				day = 0;
				dateNotice = off < 0 ? "보낸 사람이 고른 날짜(" + label + ")가 지나서 오늘 기준으로 열었어요."
						: "보낸 사람이 고른 날짜(" + label + ")는 아직 날씨를 알 수 없는 먼 날이라 오늘 기준으로 열었어요.";
			}
		}
		request.setAttribute("days", Area.dayOptions());
		request.setAttribute("selDay", day);
		request.setAttribute("dayLabel", Area.dayOptions().get(day)[1]);

		// 공유 링크(?share=번호,번호…)로 열었으면 남이 담은 코스를 읽기 전용으로 보여 준다. 아니면 내 코스
		boolean shareMode = request.getParameter("share") != null;
		request.setAttribute("shareMode", shareMode);
		List<CourseItem> items;
		if (shareMode) {
			items = loadShared(request, response, request.getParameter("share"), day, dateNotice);
		} else {
			items = CourseStore.list(request.getSession());
			List<String> ids = new ArrayList<>();
			for (CourseItem c : items) ids.add(c.getContentId());
			if (!ids.isEmpty()) request.setAttribute("shareUrl", ShareLink.url(ids, day));   // 「링크 복사」용 (상대 주소)
			request.setAttribute("icsUrl", "course/ics?day=" + day);                       // 달력 파일 받기
		}
		request.setAttribute("courseEmpty", items.isEmpty());
		request.setAttribute("stops", new ArrayList<CourseStopVO>());
		request.setAttribute("totalKm", 0.0);
		request.setAttribute("gridCount", 0);
		request.setAttribute("mapJson", "{\"points\":[],\"totalKm\":0}");

		if (!items.isEmpty()) {
			try {
				buildStops(request, items, day);
			} catch (Exception e) {
				e.printStackTrace();
				request.setAttribute("error", "내 코스를 만드는 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요.");
			}
		}
		request.getRequestDispatcher("/WEB-INF/views/course.jsp").forward(request, response);
	}

	// 공유 링크 읽기: 번호를 거르고(숫자만 · 중복 제거 · 최대 10) → 서버가 관광공사에서 다시 확인 → 화면용 속성을 채운다
	// 속성: shareIds(확인된 번호 "a,b,c") · shareCount · shareNotice(빼고 보여 준 것이 있을 때) · shareUrl · icsUrl · shareEmpty
	private List<CourseItem> loadShared(HttpServletRequest request, HttpServletResponse response, String raw, int day, String dateNotice) {
		HttpSession session = request.getSession();
		ShareLink link = ShareLink.parse(raw);
		List<String> notices = new ArrayList<>();
		if (!dateNotice.isEmpty()) notices.add(dateNotice);
		if (!link.notice().isEmpty()) notices.add(link.notice());
		List<CourseItem> items = new ArrayList<>();
		if (link.isEmpty()) {
			if (notices.isEmpty()) notices.add("공유 링크에 코스 번호가 없어요.");
		} else {
			try {
				TourDAO tour = new TourDAO(ApiKey.load(getServletContext()));
				// 관광공사에 새로 물어볼 번호 수만 센다 (이미 본 번호 · 캐시에 있는 번호로 날짜만 바꿔 보는 건 막지 않는다)
				int wait = RateLimiter.tryAcquireShare(session, SharedCourse.newLookups(tour, session, link.getIds()));
				if (wait > 0) {
					response.setStatus(429);
					response.setHeader("Retry-After", String.valueOf(wait));
					notices.add("새로운 공유 코스를 너무 자주 열었어요. " + wait + "초 뒤에 다시 열어 주세요.");
				} else {
					SharedCourse sc = SharedCourse.resolve(tour, link.getIds());
					items = sc.getItems();
					if (!sc.notice().isEmpty()) notices.add(sc.notice());
					if (items.isEmpty()) notices.add("공유된 코스를 불러오지 못했어요.");
				}
			} catch (ApiException e) {
				notices.add("공유된 코스를 불러오지 못했어요. " + e.getMessage());
			}
		}
		List<String> ok = new ArrayList<>();
		for (CourseItem c : items) ok.add(c.getContentId());
		if (!ok.isEmpty()) {
			CourseStore.rememberShareIds(session, ok);   // 「내 코스로 가져오기」는 여기서 보여 준 번호만 받는다
			request.setAttribute("shareIds", String.join(",", ok));
			request.setAttribute("shareUrl", ShareLink.url(ok, day));
			request.setAttribute("icsUrl", "course/ics?share=" + String.join(",", ok) + "&day=" + day);
		}
		request.setAttribute("shareCount", ok.size());
		request.setAttribute("shareEmpty", ok.isEmpty());
		if (!notices.isEmpty()) request.setAttribute("shareNotice", String.join(" ", notices));
		return items;
	}

	// 담은 곳마다 그 좌표의 날씨를 붙이고, 같은 격자(nx,ny)는 한 번만 조회한다
	// 0~2일 뒤는 단기예보, 3~10일 뒤는 단기 + 중기를 이어 붙인 값 (중기는 지역 단위라 오전 · 오후만 있다)
	// 곳마다 여행 지수도 붙인다 (같은 시 · 군 · 구는 새 API 자료를 한 번만 불러서 나눠 쓴다)
	private void buildStops(HttpServletRequest request, List<CourseItem> items, int day) {
		// 날씨 조회기를 못 만들면(키 문제) 날씨 없이 보여 준다
		String key = null;
		WeatherDAO weatherDao = null;
		String weatherProblem = "";
		try {
			key = ApiKey.load(getServletContext());
			weatherDao = new WeatherDAO(key);
		} catch (ApiException e) {
			weatherProblem = e.getMessage();
		}
		final String apiKey = key;
		boolean longDay = day >= 3;

		// 격자별로 한 번씩만 동시에 조회 (긴 날짜는 격자 + 지역별로 이어 붙인 예보를 기본 풀에서)
		Map<String, CompletableFuture<WeatherVO>> byGrid = new LinkedHashMap<>();
		Map<String, CompletableFuture<DayForecastVO>> byGridLong = new LinkedHashMap<>();
		if (weatherDao != null) {
			for (CourseItem it : items) {
				if (!it.isHasLocation()) continue;
				int[] g = GridConverter.toGrid(it.getLat(), it.getLon());
				String gk = g[0] + "," + g[1];
				if (longDay) {
					String lk = gk + "|" + it.getSidoCode() + "|" + it.getSigunguCode();
					if (!byGridLong.containsKey(lk)) {
						byGridLong.put(lk, ApiClient.asyncOuter(() ->
								new ForecastStitcher(apiKey).tenDays(it.getLat(), it.getLon(), it.getSidoCode(), it.getSigunguCode()).getDay(day)));
					}
				} else if (!byGrid.containsKey(gk)) {
					final WeatherDAO dao = weatherDao;
					byGrid.put(gk, ApiClient.async(() -> dao.forecast(it.getLat(), it.getLon(), day)));
				}
			}
		}
		request.setAttribute("gridCount", longDay ? byGridLong.size() : byGrid.size());

		// 새 API 자료: 같은 지역끼리 묶어 한 번씩 (「기다리는 작업」 전용 풀에서 동시에).
		// 담을 수 있는 곳이 최대 10곳이라 지역도 최대 10개. 같은 시 · 도의 에어코리아 · 특보는 ApiClient 가 한 번만 부른다(같은 주소 동시 호출 공유)
		Map<String, CompletableFuture<RegionEvidence>> evByRegion = new LinkedHashMap<>();
		if (apiKey != null) {
			for (CourseItem it : items) {
				String rk = it.getSidoCode() + "|" + it.getSigunguCode();
				if (evByRegion.containsKey(rk) || RegionCodes.of(it.getSidoCode()) == null) continue;
				evByRegion.put(rk, ApiClient.asyncOuter(() ->
						RegionEvidence.load(apiKey, it.getSidoCode(), it.getSigunguCode(), it.getAddr(), it.getLat(), it.getLon(), null)));
			}
		}

		List<CourseStopVO> stops = new ArrayList<>();
		JsonArray points = new JsonArray();
		List<String> warnTexts = new ArrayList<>();
		Map<String, RegionEvidence> evDone = new LinkedHashMap<>();
		int[][] times = IcsBuilder.times(items);   // 예상 시간: 캘린더(.ics)와 같은 규칙 (코스 순서대로, 겹치지 않게)
		double total = 0;
		int failed = 0;
		boolean anyMid = false;
		String midNote = "";
		for (int i = 0; i < items.size(); i++) {
			CourseItem it = items.get(i);
			CourseStopVO stop = new CourseStopVO();
			stop.setNo(i + 1);
			stop.setItem(it);
			stop.setFirst(i == 0);
			stop.setLast(i == items.size() - 1);
			stop.setWarn("");
			stop.setWeatherError("");
			stop.setTime(times[i][0], times[i][1]);
			stop.setEventNotice(it.eventNotice(Area.dayOf(day)));   // 행사가 고른 날에 안 열리면 안내

			if (!it.isHasLocation()) {
				stop.setWeatherError("좌표 정보가 없어 날씨와 지도에서는 빠져요.");
			} else if (weatherDao == null) {
				stop.setWeatherError(weatherProblem);
			} else {
				int[] g = GridConverter.toGrid(it.getLat(), it.getLon());
				try {
					WeatherVO w;
					if (longDay) {
						DayForecastVO f = byGridLong.get(g[0] + "," + g[1] + "|" + it.getSidoCode() + "|" + it.getSigunguCode()).join();
						w = f.getWeather();
						if (DayForecastVO.MID.equals(f.getSource())) {
							anyMid = true;
							midNote = f.getNote();
						}
						if (w == null) throw new ApiException(f.getNote());
					} else {
						w = ApiClient.await(byGrid.get(g[0] + "," + g[1]));
					}
					stop.setWeather(w);
					if (!it.isIndoor() && w.isRainy()) {
						String when = w.getRainTime().isEmpty() ? "" : " (" + w.getRainTime() + "부터)";
						String head = it.isPlaceUnknown() ? "야외에서 열린다면 " : "실외 관광지인데 ";   // 행사는 야외인지 모를 때가 있다
						stop.setWarn(head + w.getRainType() + " 예보" + when + "가 있어요. 실내 일정으로 바꾸거나 다른 날을 골라 보세요.");
					}
				} catch (ApiException e) {
					stop.setWeatherError(e.getMessage());
					failed++;
				} catch (Exception e) {
					stop.setWeatherError("날씨를 불러오지 못했어요.");
					failed++;
				}
			}

			// 여행 지수 (이 곳 지역의 새 API 자료가 없으면 그 근거만 "자료 없음")
			String rk = it.getSidoCode() + "|" + it.getSigunguCode();
			RegionEvidence ev = null;
			if (evByRegion.containsKey(rk)) {
				ev = evDone.get(rk);
				if (ev == null) {
					try {
						ev = evByRegion.get(rk).join();
						evDone.put(rk, ev);
						for (WarningItem w : ev.hereCurrent()) if (!warnTexts.contains(w.getText())) warnTexts.add(w.getText());
						for (WarningItem w : ev.herePrelimOn(day)) if (!warnTexts.contains(w.getText())) warnTexts.add(w.getText());
					} catch (Exception e) {
						ev = null;
					}
				}
			}
			Double crowd = ev == null ? null : ev.crowdOfSpot(it.getTitle(), day);
			stop.setCrowdRate(crowd);
			stop.setScore(TravelScore.score(ev, day, stop.getWeather(), it.isIndoor(), crowd));

			// 다음 곳까지 직선거리 (둘 다 좌표가 있을 때만)
			if (i + 1 < items.size()) {
				CourseItem next = items.get(i + 1);
				if (it.isHasLocation() && next.isHasLocation()) {
					double km = Geo.distanceKm(it.getLat(), it.getLon(), next.getLat(), next.getLon());
					stop.setNextKm(Math.round(km * 10) / 10.0);
					total += km;
				}
			}
			stops.add(stop);

			// 지도용 점
			if (it.isHasLocation()) {
				JsonObject p = new JsonObject();
				p.addProperty("n", i + 1);
				p.addProperty("id", it.getContentId());
				p.addProperty("title", it.getTitle());
				p.addProperty("lat", it.getLat());
				p.addProperty("lon", it.getLon());
				p.addProperty("indoor", it.isIndoor());
				p.addProperty("type", it.getTypeName());
				p.addProperty("url", "detail?id=" + it.getContentId() + "&type=" + it.getContentTypeId() + "&day=" + day);
				WeatherVO w = stop.getWeather();
				p.addProperty("icon", w == null ? "" : w.getIcon());
				p.addProperty("temp", w == null ? "" : w.getTempRange());
				p.addProperty("pop", w == null ? -1 : w.getMaxPop());
				p.addProperty("warn", !stop.getWarn().isEmpty());
				MapJson.addScore(p, stop.getScore());
				points.add(p);
			}
		}
		if (failed > 0) {
			request.setAttribute("weatherNotice", "일부 날씨를 불러오지 못했어요. 잠시 뒤 새로고침해 보세요.");
		}
		request.setAttribute("midDay", anyMid);
		request.setAttribute("midNote", midNote);
		request.setAttribute("warnTexts", warnTexts); // 담은 곳 지역에 발효 중이거나 예고된 특보 (없으면 빈 목록)
		request.setAttribute("warnFar", day >= 2);    // 모레부터는 「지금 발효 중인 특보」일 뿐 그 날 특보는 아직 모른다
		double totalKm = Math.round(total * 10) / 10.0;
		JsonObject map = new JsonObject();
		map.add("points", points);
		map.addProperty("totalKm", totalKm);

		request.setAttribute("stops", stops);
		request.setAttribute("totalKm", totalKm);
		request.setAttribute("mapJson", GSON.toJson(map));
	}

	// ---------------- POST: 담기 · 빼기 · 순서 · 비우기 ----------------
	// 답 한 건
	private static class Reply {
		int status = 200;
		boolean ok = true;
		String code = "OK";
		String message = "";
		boolean changed = false;   // 코스가 실제로 바뀌었나
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		boolean wantsJson = wantsJson(request);
		HttpSession session = request.getSession();
		String action = request.getParameter("action");
		String id = request.getParameter("id");

		Reply r = new Reply();
		String sent = request.getParameter("csrf");
		if (sent == null) sent = request.getHeader("X-CSRF-Token");
		if (!CourseStore.checkToken(session, sent)) {
			// 토큰이 다르면 아무것도 바꾸지 않는다
			fail(r, 403, "CSRF", "요청이 만료됐어요. 화면을 새로고침한 뒤 다시 시도해 주세요.");
		} else if (action == null) {
			fail(r, 400, "BAD_ACTION", "요청 종류가 없어요.");
		} else if ((action.equals("add") || action.equals("addPlan") || action.equals("import")) && tooManyAdds(session, r)) {
			// 담기는 관광지 확인 호출을 부르므로 횟수를 제한했다 (r 에 안내 문구가 채워졌다)
		} else {
			switch (action) {
			case "add":
				add(session, id, r);
				break;
			case "remove":
				if (!validId(id)) {
					fail(r, 400, "BAD_ID", "관광지 번호가 올바르지 않아요.");
				} else if (CourseStore.remove(session, id)) {
					r.changed = true;
					r.message = "내 코스에서 뺐어요.";
				} else {
					fail(r, 404, "NOT_IN_COURSE", "내 코스에 없는 곳이에요.");
				}
				break;
			case "up":
			case "down":
				if (!validId(id)) {
					fail(r, 400, "BAD_ID", "관광지 번호가 올바르지 않아요.");
				} else if (CourseStore.move(session, id, action.equals("up") ? -1 : 1)) {
					r.changed = true;
					r.message = action.equals("up") ? "한 칸 위로 옮겼어요." : "한 칸 아래로 옮겼어요.";
				} else {
					r.message = "더 옮길 수 없어요.";
				}
				break;
			case "clear":
				CourseStore.clear(session);
				r.changed = true;
				r.message = "내 코스를 비웠어요.";
				break;
			case "addPlan":
				addMany(session, request.getParameter("ids"), r, false);
				break;
			case "import":   // 공유받은 코스를 내 코스로 가져오기 (공유 화면이 보여 준 번호만)
				addMany(session, request.getParameter("ids"), r, true);
				break;
			default:
				fail(r, 400, "BAD_ACTION", "알 수 없는 요청이에요.");
			}
		}

		if (wantsJson) {
			writeJson(response, session, action, id, r);
		} else {
			// PRG: 결과를 알림으로 남기고 화면으로 되돌린다
			CourseStore.flash(session, r.message, r.ok);
			String next = request.getParameter("next");
			if (next != null) next = next.replaceAll(";jsessionid=[A-Za-z0-9._-]+", ""); // c:url 이 붙인 세션 꼬리표 제거
			String target = next != null && NEXT.matcher(next).matches() ? next : "course";
			response.setStatus(HttpServletResponse.SC_SEE_OTHER);
			response.setHeader("Location", response.encodeRedirectURL(request.getContextPath() + "/" + target));
		}
	}

	// 담기 요청이 너무 잦으면 막는다 (세션마다 1분에 20번)
	private boolean tooManyAdds(HttpSession session, Reply r) {
		int wait = RateLimiter.tryAcquireAdd(session);
		if (wait == 0) return false;
		fail(r, 429, "RATE_LIMIT", "담기 요청이 너무 많아요. " + wait + "초 뒤에 다시 시도해 주세요.");
		return true;
	}

	// 한 곳 담기: 클라이언트가 보낸 제목 · 좌표는 안 쓰고 contentId 로 서버가 detailCommon2 를 다시 조회(캐시됨)
	private void add(HttpSession session, String id, Reply r) {
		if (!validId(id)) {
			fail(r, 400, "BAD_ID", "관광지 번호가 올바르지 않아요.");
			return;
		}
		// 이미 담았거나 가득 찼으면 API 를 부르기 전에 답한다
		if (CourseStore.ids(session).contains(id)) {
			r.code = "DUPLICATE";
			r.message = "이미 내 코스에 담긴 곳이에요.";
			return;
		}
		if (CourseStore.count(session) >= CourseStore.MAX) {
			fail(r, 409, "FULL", "내 코스는 " + CourseStore.MAX + "곳까지 담을 수 있어요. 하나를 빼고 다시 담아 주세요.");
			return;
		}
		TourVO vo;
		try {
			vo = new TourDAO(ApiKey.load(getServletContext())).detail(id);
		} catch (ApiException e) {
			fail(r, 502, "API", "관광지 정보를 확인하지 못했어요. " + e.getMessage());
			return;
		}
		if (vo == null) {
			fail(r, 404, "NOT_FOUND", "없는 관광지예요.");
			return;
		}
		CourseItem item = CourseItem.from(vo);
		applyEventPeriod(session, item);
		String result = CourseStore.add(session, item);
		if (result.equals(CourseStore.ADDED)) {
			r.changed = true;
			r.message = addedMessage(vo.getTitle());
		} else if (result.equals(CourseStore.DUPLICATE)) {
			r.code = "DUPLICATE";
			r.message = "이미 내 코스에 담긴 곳이에요.";
		} else {
			fail(r, 409, "FULL", "내 코스는 " + CourseStore.MAX + "곳까지 담을 수 있어요. 하나를 빼고 다시 담아 주세요.");
		}
	}

	// 행사(15)는 목록 · 상세에서 본 기간을 붙인다 (없으면 기간을 모르는 채로 담긴다)
	private static void applyEventPeriod(HttpSession session, CourseItem item) {
		if (!"15".equals(item.getContentTypeId())) return;
		String[] p = CourseStore.eventPeriodOf(session, item.getContentId());
		if (p != null && p.length == 2) item.setEventPeriod(p[0], p[1]);
	}

	// 여러 곳 한꺼번에 담기: ids = "id1,id2,..." (순서대로). 각 번호를 서버가 다시 확인한다
	//   fromShare=false  하루 코스 통째로 담기(addPlan): /plan 이 하루 코스로 보여 준 번호만 받고, 그 칸 시간도 같이 담는다
	//   fromShare=true   공유받은 코스 가져오기(import): 공유 화면이 보여 준 번호만 받는다
	private void addMany(HttpSession session, String ids, Reply r, boolean fromShare) {
		List<String> list = new ArrayList<>();
		int notShown = 0;
		if (ids != null) {
			for (String s : ids.split(",")) {
				s = s.trim();
				if (!validId(s) || list.contains(s)) continue;
				boolean shown = fromShare ? CourseStore.isShownShareId(session, s) : CourseStore.isShownPlanId(session, s);
				if (!shown) { notShown++; continue; } // 화면이 보여 준 번호만 담는다 (아무 번호나 보내 API 를 마구 부르지 못하게)
				list.add(s);
				if (list.size() >= CourseStore.MAX) break;
			}
		}
		if (list.isEmpty()) {
			if (notShown > 0) {
				fail(r, 400, "BAD_ID", fromShare
						? "이 공유 코스는 만료됐어요. 공유 링크를 다시 열어 주세요."
						: "이 코스는 만료됐어요. 하루 코스 화면을 새로고침한 뒤 다시 담아 주세요.");
			} else {
				fail(r, 400, "BAD_ID", "담을 관광지가 없어요.");
			}
			return;
		}
		TourDAO tour;
		try {
			tour = new TourDAO(ApiKey.load(getServletContext()));
		} catch (ApiException e) {
			fail(r, 502, "API", e.getMessage());
			return;
		}
		// 확인은 동시에, 담기는 순서대로
		int added = 0, dup = 0, full = 0, bad = 0, missing = 0;
		List<CompletableFuture<TourVO>> futures = new ArrayList<>();
		for (String id : list) {
			if (CourseStore.ids(session).contains(id)) {
				dup++;                       // 이미 담은 곳은 다시 확인하지 않는다
			} else if (futures.size() >= CourseStore.MAX - CourseStore.count(session)) {
				full++;                      // 자리가 없으면 확인하지 않는다
			} else {
				futures.add(ApiClient.async(() -> tour.detail(id)));
			}
		}
		for (CompletableFuture<TourVO> f : futures) {
			try {
				TourVO vo = ApiClient.await(f);
				if (vo == null) { missing++; continue; }
				CourseItem item = CourseItem.from(vo);
				if (!fromShare) item.setSlotFromLabel(CourseStore.planSlotLabel(session, item.getContentId())); // 하루 코스 칸 시간
				String result = CourseStore.add(session, item);
				if (result.equals(CourseStore.ADDED)) added++;
				else if (result.equals(CourseStore.DUPLICATE)) dup++;
				else full++;
			} catch (ApiException e) {
				bad++;
			}
		}
		if (added == 0 && dup > 0 && full == 0 && bad == 0 && missing == 0) {
			r.message = "이미 모두 내 코스에 담겨 있어요.";
			r.ok = false;
			r.code = "DUPLICATE";
			return;
		}
		StringBuilder sb = new StringBuilder(added + "곳을 내 코스에 담았어요.");
		if (dup > 0) sb.append(" (이미 담긴 ").append(dup).append("곳 제외)");
		if (full > 0) sb.append(" (가득 차서 ").append(full).append("곳은 못 담았어요)");
		if (bad + missing > 0) sb.append(" (확인하지 못한 ").append(bad + missing).append("곳 제외)");
		r.message = sb.toString();
		r.changed = added > 0;
		if (added == 0) {
			r.ok = false;
			// 없는 번호만이면 404, 호출 실패가 섞였으면 502
			r.status = full > 0 ? 409 : (bad > 0 ? 502 : (missing > 0 ? 404 : 200));
			r.code = full > 0 ? "FULL" : (bad > 0 ? "API" : (missing > 0 ? "NOT_FOUND" : "DUPLICATE"));
		}
	}

	// '제목' + 을/를 (제목 마지막 글자가 한글이면 받침을 보고 고른다. 한글이 아니면 조사 없이 쓴다)
	private static String addedMessage(String title) {
		if (title == null || title.isEmpty()) return "내 코스에 담았어요.";
		char c = title.charAt(title.length() - 1);
		if (c >= 0xAC00 && c <= 0xD7A3) {
			return "'" + title + "'" + ((c - 0xAC00) % 28 != 0 ? "을" : "를") + " 내 코스에 담았어요.";
		}
		return "내 코스에 담았어요. ('" + title + "')";
	}

	private static void fail(Reply r, int status, String code, String message) {
		r.ok = false;
		r.status = status;
		r.code = code;
		r.message = message;
	}

	private static boolean validId(String id) {
		return id != null && id.matches("\\d{1,12}");
	}

	private static boolean wantsJson(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		return (accept != null && accept.contains("application/json")) || request.getHeader("X-Requested-With") != null;
	}

	// JSON 답: {"ok":true,"code":"OK","action":"add","id":"123","changed":true,"message":"..","count":3,"max":10,"ids":["..",".."]}
	private void writeJson(HttpServletResponse response, HttpSession session, String action, String id, Reply r) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("ok", r.ok);
		o.addProperty("code", r.code);
		o.addProperty("action", action == null ? "" : action);
		o.addProperty("id", validId(id) ? id : "");
		o.addProperty("changed", r.changed);
		o.addProperty("message", r.message);
		o.addProperty("count", CourseStore.count(session));
		o.addProperty("max", CourseStore.MAX);
		JsonArray ids = new JsonArray();
		for (String s : CourseStore.ids(session)) ids.add(s);
		o.add("ids", ids);

		response.setStatus(r.status);
		response.setContentType("application/json; charset=UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.getWriter().write(GSON.toJson(o));
	}
}
