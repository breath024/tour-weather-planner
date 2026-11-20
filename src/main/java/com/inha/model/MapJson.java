package com.inha.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

// 지도용 JSON 만들기 (서버에서 Gson 으로). 화면은 <script type="application/json"> 에 넣고 JS 가 JSON.parse 한다
// Gson 기본값(HTML 이스케이프 켜짐)을 그대로 써서 < > & ' = 가 < 같은 모양이 된다 → script 태그 안에 넣어도 안전. 끄지 말 것
public class MapJson {

	private static final Gson GSON = new Gson();

	public static String toText(JsonObject o) {
		return GSON.toJson(o);
	}

	// 관광지 상세 링크
	public static String detailUrl(String id, String typeId, int day, String area, String sigungu) {
		String u = "detail?id=" + id + "&type=" + typeId + "&day=" + day;
		if (area != null && !area.isEmpty()) u += "&area=" + area;
		if (sigungu != null && !sigungu.isEmpty()) u += "&sigungu=" + sigungu;
		return u;
	}

	// 마커 하나: 관광지 + 그 자리 날씨 (좌표 없는 곳은 부르는 쪽에서 뺀다)
	public static JsonObject spot(TourVO s, int day, String area, String sigungu) {
		JsonObject p = new JsonObject();
		p.addProperty("id", s.getContentId());
		p.addProperty("title", s.getTitle());
		p.addProperty("lat", s.getLat());
		p.addProperty("lon", s.getLon());
		p.addProperty("indoor", s.isIndoor());
		p.addProperty("type", s.getTypeName());
		p.addProperty("image", s.getImage());
		p.addProperty("url", detailUrl(s.getContentId(), s.getContentTypeId(), day, area, sigungu));
		WeatherVO w = s.getWeather();
		p.addProperty("icon", w == null ? "" : w.getIcon());
		p.addProperty("temp", w == null ? "" : w.getTempRange());
		p.addProperty("pop", w == null ? -1 : w.getMaxPop());
		p.addProperty("approx", s.isWeatherApprox());
		p.addProperty("fit", s.isFit());
		p.addProperty("mode", s.getRecMode() == null ? "" : s.getRecMode());
		addScore(p, s.getScore());
		p.addProperty("crowd", s.getCrowdText());
		p.addProperty("cat", s.getLclsCode());       // 3판: 대분류 코드 (없으면 "")
		p.addProperty("catName", s.getLclsName());
		return p;
	}

	// 5판: 추천 동선 (출발점에서 주변 관광지를 도는 순서 + 어림 시각 · 이동 시간)
	public static JsonObject route(NearbyRoute.Result r, int day, String area, String sigungu) {
		JsonObject o = new JsonObject();
		o.addProperty("available", r.isAvailable());
		o.addProperty("note", r.getNote());
		if (!r.isAvailable()) return o;
		o.addProperty("start", r.getStart());
		o.addProperty("end", r.getEnd());
		o.addProperty("totalKm", r.getTotalKm());
		o.addProperty("moveMin", r.getMoveMin());
		JsonArray stops = new JsonArray();
		for (NearbyRoute.Stop st : r.getStops()) {
			TourVO s = st.getSpot();
			JsonObject p = new JsonObject();
			p.addProperty("n", st.getNo());
			p.addProperty("id", s.getContentId());
			p.addProperty("title", s.getTitle());
			p.addProperty("type", s.getTypeName());
			p.addProperty("lat", s.getLat());
			p.addProperty("lon", s.getLon());
			p.addProperty("indoor", s.isIndoor());
			p.addProperty("fit", s.isFit());
			p.addProperty("image", s.getImage());
			p.addProperty("url", detailUrl(s.getContentId(), s.getContentTypeId(), day, area, sigungu));
			p.addProperty("arrive", st.getArrive());
			p.addProperty("leave", st.getLeave());
			p.addProperty("legKm", st.getLegKm());
			p.addProperty("legMin", st.getLegMin());
			p.addProperty("legText", st.getLegText());
			p.addProperty("walk", st.isWalk());
			p.addProperty("afterSunset", st.isAfterSunset());
			WeatherVO w = s.getWeather();
			p.addProperty("icon", w == null ? "" : w.getIcon());
			stops.add(p);
		}
		o.add("stops", stops);
		return o;
	}

	// 3판: 축제 · 행사 마커 하나 (그 행사 자리 격자 날씨 + 야외 행사 비 경고)
	public static JsonObject event(TourVO e, int day, String area, String sigungu) {
		JsonObject p = new JsonObject();
		p.addProperty("id", e.getContentId());
		p.addProperty("title", e.getTitle());
		p.addProperty("lat", e.getLat());
		p.addProperty("lon", e.getLon());
		p.addProperty("indoor", e.isIndoor());
		p.addProperty("placeUnknown", e.isPlaceUnknown());   // 실내 · 야외를 모르는 행사
		p.addProperty("period", e.getEventPeriod());
		p.addProperty("url", detailUrl(e.getContentId(), e.getContentTypeId(), day, area, sigungu));
		WeatherVO w = e.getWeather();
		p.addProperty("icon", w == null ? "" : w.getIcon());
		p.addProperty("temp", w == null ? "" : w.getTempRange());
		p.addProperty("pop", w == null ? -1 : w.getMaxPop());
		p.addProperty("warn", !e.getEventWarn().isEmpty());
		return p;
	}

	// 마커용 짧은 점수 칸: score(없으면 -1) · grade · gkey(best/good/ok/bad/none)
	public static void addScore(JsonObject p, ScoreVO sc) {
		boolean ok = sc != null && sc.isAvailable();
		p.addProperty("score", ok ? sc.getScore() : -1);
		p.addProperty("grade", ok ? sc.getGrade() : "");
		p.addProperty("gkey", ok ? sc.getGradeKey() : "none");
	}

	// 여행 지수 전체 → JSON (칩 포함)
	public static JsonObject score(ScoreVO sc) {
		JsonObject o = new JsonObject();
		boolean ok = sc != null && sc.isAvailable();
		o.addProperty("available", ok);
		o.addProperty("score", ok ? sc.getScore() : -1);
		o.addProperty("grade", sc == null ? "" : sc.getGrade());
		o.addProperty("gkey", sc == null ? "none" : sc.getGradeKey());
		o.addProperty("note", sc == null ? "" : sc.getNote());
		o.addProperty("missing", sc == null ? 0 : sc.getMissing());
		o.addProperty("uncertain", sc != null && sc.isUncertain());          // 3판: 다른 날에는 있는 근거가 이 날은 빠짐 → 점수 90 상한 · 신뢰도 낮음
		o.addProperty("uncertainNote", sc == null ? "" : sc.getUncertainNote());
		o.addProperty("confidence", sc == null ? "" : sc.getConfidence());
		JsonArray chips = new JsonArray();
		if (sc != null) {
			for (ChipVO c : sc.getChips()) {
				JsonObject x = new JsonObject();
				x.addProperty("key", c.getKey());
				x.addProperty("label", c.getLabel());
				x.addProperty("text", c.getText());
				x.addProperty("level", c.getLevel());
				x.addProperty("delta", c.getDelta());
				x.addProperty("hint", c.getHint());
				chips.add(x);
			}
		}
		o.add("chips", chips);
		return o;
	}

	// 그 날의 새 API 자료 요약 → JSON (/api/around 에서 쓴다). 자료가 없는 칸은 null + 이유
	public static JsonObject evidence(RegionEvidence ev, int day, SunVO sun) {
		JsonObject o = new JsonObject();
		RegionEvidence.AirView a = ev.airView(day);
		if (a == null) {
			o.add("air", null);
			o.addProperty("airError", ev.airMissingReason(day));
		} else {
			JsonObject x = new JsonObject();
			x.addProperty("grade", a.getGrade());
			x.addProperty("gradeText", a.getGradeText());
			x.addProperty("source", a.getSource());
			x.addProperty("pm10", a.getPm10Text());
			x.addProperty("pm25", a.getPm25Text());
			x.addProperty("time", a.getTime());
			x.addProperty("scope", a.getScope());
			o.add("air", x);
			o.addProperty("airError", "");
		}
		RegionEvidence.UvView u = ev.uvView(day);
		if (u == null) {
			o.add("uv", null);
			o.addProperty("uvError", ev.uvMissingReason(day));
		} else {
			JsonObject x = new JsonObject();
			x.addProperty("max", u.getMax());
			x.addProperty("level", u.getLevelText());
			x.addProperty("advice", u.getAdvice());
			o.add("uv", x);
			o.addProperty("uvError", "");
		}
		if (sun != null && sun.isHasTimes()) {
			JsonObject x = new JsonObject();
			x.addProperty("sunrise", sun.getSunrise());
			x.addProperty("sunset", sun.getSunset());
			o.add("sun", x);
		} else {
			o.add("sun", null);
		}
		JsonArray warns = new JsonArray();
		for (WarningItem w : ev.hereCurrent()) warns.add(w.getText());
		for (WarningItem w : ev.herePrelimOn(day)) warns.add(w.getText());
		o.add("warnings", warns);
		o.addProperty("warnFar", day >= 2); // 모레부터는 「지금 발효 중인 특보」일 뿐 그 날 특보는 아직 모른다 → 화면이 차분한 안내로 보인다
		Double avg = ev.crowdAverage(day);
		if (avg == null) {
			o.add("crowd", null);
		} else {
			o.addProperty("crowd", Math.round(avg));
			o.addProperty("crowdText", Math.round(avg) + "% (" + CrowdVO.levelText(avg) + ")");
		}
		return o;
	}

	// /plan: 전체 관광지(자기 자리 날씨 포함) + 하루 코스 번호 마커 · 경로
	public static String plan(List<TourVO> spots, DayPlanVO plan, double[] center, int day, String area, String sigungu) {
		return plan(spots, plan, center, day, area, sigungu, null, "");
	}

	// events: 이 날 열리는 축제 · 행사(없으면 null) → "events" 칸. selCat: 서버가 걸러 낸 분류(없으면 "") → "cat" 칸
	// spots 는 이미 ?cat= 으로 거른 목록이고, 하루 코스(course)는 거르기 전 전체 기준이다
	public static String plan(List<TourVO> spots, DayPlanVO plan, double[] center, int day, String area, String sigungu,
			List<TourVO> events, String selCat) {
		return plan(spots, plan, center, day, area, sigungu, events, selCat, null, 12);
	}

	// fc · nowHour: 시간 슬라이더용 "time" 칸을 만든다 (3판). fc = 그 날 이어 붙인 예보(중기예보 날이면 오전 · 오후 2단계), nowHour = 지금 시(오늘 기본 칸을 정할 때)
	// 추가 API 호출은 없다: 이미 관광지에 붙어 있는 격자 날씨(WeatherVO.hours)를 모아 옮길 뿐이다
	public static String plan(List<TourVO> spots, DayPlanVO plan, double[] center, int day, String area, String sigungu,
			List<TourVO> events, String selCat, DayForecastVO fc, int nowHour) {
		TimeData td = new TimeData(fc, day, nowHour);
		JsonObject o = new JsonObject();
		o.addProperty("cat", selCat == null ? "" : selCat);
		JsonArray evList = new JsonArray();
		if (events != null) {
			for (TourVO e : events) {
				if (e.isHasLocation()) evList.add(withGrid(event(e, day, area, sigungu), td.add(e)));
			}
		}
		o.add("events", evList);
		if (center != null) {
			JsonObject c = new JsonObject();
			c.addProperty("lat", center[0]);
			c.addProperty("lon", center[1]);
			o.add("center", c);
		} else {
			o.add("center", null);
		}
		JsonArray list = new JsonArray();
		for (TourVO s : spots) {
			if (s.isHasLocation()) list.add(withGrid(spot(s, day, area, sigungu), td.add(s)));
		}
		o.add("spots", list);
		o.add("time", td.toJson());

		JsonArray course = new JsonArray();
		double km = 0;
		if (plan != null && plan.isAvailable()) {
			for (PlanBlockVO b : plan.getBlocks()) {
				TourVO s = b.getSpot();
				JsonObject p = new JsonObject();
				p.addProperty("n", b.getNo());
				p.addProperty("id", s.getContentId());
				p.addProperty("title", s.getTitle());
				p.addProperty("lat", s.getLat());
				p.addProperty("lon", s.getLon());
				p.addProperty("indoor", s.isIndoor());
				p.addProperty("time", b.getTimeLabel());
				p.addProperty("icon", b.getIcon());
				p.addProperty("sky", b.getSky());
				p.addProperty("type", s.getTypeName());
				p.addProperty("temp", b.getTemp());
				p.addProperty("pop", b.getPop());
				p.addProperty("warn", !b.getWarn().isEmpty());
				p.addProperty("next", b.getNextMove()); // 다음 곳까지 어림 이동 시간 (마지막은 "")
				p.addProperty("url", detailUrl(s.getContentId(), s.getContentTypeId(), day, area, sigungu));
				course.add(p);
			}
			km = plan.getTotalKm();
		}
		o.add("course", course);
		o.addProperty("courseKm", km);
		return toText(o);
	}

	// /detail: 이 관광지 + 주변 관광지
	public static String detail(TourVO spot, List<TourVO> nearby, WeatherVO w, int day, String area, String sigungu) {
		JsonObject o = new JsonObject();
		if (spot != null && spot.isHasLocation()) {
			JsonObject p = new JsonObject();
			p.addProperty("id", spot.getContentId());
			p.addProperty("title", spot.getTitle());
			p.addProperty("lat", spot.getLat());
			p.addProperty("lon", spot.getLon());
			p.addProperty("indoor", spot.isIndoor());
			p.addProperty("type", spot.getTypeName());
			p.addProperty("icon", w == null ? "" : w.getIcon());
			p.addProperty("temp", w == null ? "" : w.getTempRange());
			p.addProperty("pop", w == null ? -1 : w.getMaxPop());
			addScore(p, spot.getScore());
			o.add("spot", p);
		} else {
			o.add("spot", null);
		}
		JsonArray list = new JsonArray();
		if (nearby != null) {
			for (TourVO s : nearby) {
				if (!s.isHasLocation()) continue;
				JsonObject p = new JsonObject();
				p.addProperty("id", s.getContentId());
				p.addProperty("title", s.getTitle());
				p.addProperty("lat", s.getLat());
				p.addProperty("lon", s.getLon());
				p.addProperty("indoor", s.isIndoor());
				p.addProperty("type", s.getTypeName());
				p.addProperty("distKm", s.getDistKm());
				p.addProperty("url", detailUrl(s.getContentId(), s.getContentTypeId(), day, area, sigungu));
				list.add(p);
			}
		}
		o.add("nearby", list);
		return toText(o);
	}

	// 날씨 요약 (+ 시간별) → JSON. /api/around 에서 쓴다
	public static JsonObject weather(WeatherVO w) {
		JsonObject o = new JsonObject();
		o.addProperty("dateLabel", w.getDateLabel());
		o.addProperty("sky", w.getSky());
		o.addProperty("icon", w.getIcon());
		o.addProperty("tempRange", w.getTempRange());
		if (w.getMinTemp() == null) o.add("minTemp", null); else o.addProperty("minTemp", w.getMinTemp());
		if (w.getMaxTemp() == null) o.add("maxTemp", null); else o.addProperty("maxTemp", w.getMaxTemp());
		o.addProperty("maxPop", w.getMaxPop());
		o.addProperty("rainy", w.isRainy());
		o.addProperty("rainType", w.getRainType());
		o.addProperty("rainTime", w.getRainTime());
		o.addProperty("partial", w.isPartial());
		o.addProperty("nx", w.getNx());
		o.addProperty("ny", w.getNy());
		o.addProperty("baseDate", w.getBaseDate());
		o.addProperty("baseTime", w.getBaseTime());
		JsonArray hours = new JsonArray();
		for (HourVO h : w.getHours()) {
			JsonObject x = new JsonObject();
			x.addProperty("time", h.getTime());
			x.addProperty("temp", h.getTemp());
			x.addProperty("pop", h.getPop());
			x.addProperty("sky", h.getSky());
			x.addProperty("icon", h.getIcon());
			hours.add(x);
		}
		o.add("hours", hours);
		return o;
	}

	// 추천 → JSON
	public static JsonObject rec(RecommendVO r) {
		JsonObject o = new JsonObject();
		o.addProperty("mode", r.getMode());
		o.addProperty("title", r.getTitle());
		o.addProperty("reason", r.getReason());
		o.addProperty("clothes", r.getClothes());
		o.addProperty("umbrella", r.getUmbrella());
		o.addProperty("mask", r.getMask());
		return o;
	}

	// 마커에 격자 id("g") 를 붙인다. 날씨가 없으면 "g" 칸이 없다 (슬라이더가 이 마커는 건드리지 않는다)
	private static JsonObject withGrid(JsonObject p, String gid) {
		if (gid != null) p.addProperty("g", gid);
		return p;
	}

	// ---- 3판: 시간 슬라이더용 자료 ("time") ----
	// 모양:
	//  {"mode":"hourly","steps":["06시","07시",…],"hours":[6,7,…],"def":8,"partial":false,"tempNote":"",
	//   "skies":[["맑음","sun"],["비","rain"]],   (둘째 칸 = 아이콘 이름표)
	//   "grids":{"89_91":{"nx":89,"ny":91,"t":[1,1,…],"p":[0,10,…],"s":[0,0,1,…]}}}
	//  · steps 칸 순서대로 격자마다 기온(t, 없으면 null) · 강수확률(p, 없으면 null) · 하늘 번호(s = skies 의 번호, 없으면 null)
	//  · 관광지 · 행사 마커의 "g" 가 grids 의 열쇠 → 마커 하나마다 날씨를 또 담지 않아 작다
	//  · 중기예보 날은 mode "halfday": steps ["오전","오후"], 격자는 "m" 하나(지역 단위 예보라 모든 마커가 같다),
	//    t = [그 날 최저, 그 날 최고] (오전 · 오후 기온이 따로 없어서 tempNote 로 밝힌다)
	//  · 날씨가 하나도 없으면 {"mode":"none"}
	static class TimeData {
		private final boolean mid;
		private final DayForecastVO fc;
		private final int day, nowHour;
		private final Map<String, WeatherVO> grids = new LinkedHashMap<>();

		TimeData(DayForecastVO fc, int day, int nowHour) {
			this.fc = fc;
			this.day = day;
			this.nowHour = nowHour;
			this.mid = fc != null && DayForecastVO.MID.equals(fc.getSource());
		}

		// 마커의 날씨를 모으고 격자 id 를 돌려준다 (날씨가 없으면 null)
		String add(TourVO s) {
			WeatherVO w = s.getWeather();
			if (w == null) return null;
			if (mid) return "m";
			if (w.getHours() == null || w.getHours().isEmpty()) return null;
			String id = w.getNx() + "_" + w.getNy();
			grids.putIfAbsent(id, w);
			return id;
		}

		JsonObject toJson() {
			JsonObject o = new JsonObject();
			if (mid) {
				if (!fc.isAvailable()) return none();
				o.addProperty("mode", "halfday");
				JsonArray steps = new JsonArray();
				steps.add("오전");
				steps.add("오후");
				o.add("steps", steps);
				o.add("hours", new JsonArray());
				o.addProperty("def", 1);
				o.addProperty("partial", false);
				o.addProperty("tempNote", "중기예보라 기온은 그 날 최저(오전) · 최고(오후)만 나와요.");
				List<String[]> skies = new ArrayList<>();
				JsonObject g = new JsonObject();
				JsonArray t = new JsonArray(), p = new JsonArray(), sk = new JsonArray();
				addNum(t, fc.getMinTemp());
				addNum(t, fc.getMaxTemp());
				addNum(p, fc.getAmPop() < 0 ? null : fc.getAmPop());
				addNum(p, fc.getPmPop() < 0 ? null : fc.getPmPop());
				addNum(sk, skyIndex(skies, fc.getAmSky(), fc.getAmIcon()));
				addNum(sk, skyIndex(skies, fc.getPmSky(), fc.getPmIcon()));
				g.add("t", t);
				g.add("p", p);
				g.add("s", sk);
				JsonObject gs = new JsonObject();
				gs.add("m", g);
				o.add("skies", skiesJson(skies));
				o.add("grids", gs);
				return o;
			}
			if (grids.isEmpty()) return none();
			// 모든 격자에 나온 시각을 합쳐 06~23시만, 작은 시간부터
			TreeSet<Integer> hourSet = new TreeSet<>();
			for (WeatherVO w : grids.values()) {
				for (HourVO h : w.getHours()) {
					int hr = h.getHourOfDay();
					if (hr >= 6 && hr <= 23) hourSet.add(hr);
				}
			}
			if (hourSet.isEmpty()) return none();
			List<Integer> hours = new ArrayList<>(hourSet);
			o.addProperty("mode", "hourly");
			JsonArray steps = new JsonArray(), hs = new JsonArray();
			for (int hr : hours) {
				steps.add(String.format("%02d시", hr));
				hs.add(hr);
			}
			o.add("steps", steps);
			o.add("hours", hs);
			o.addProperty("def", defaultStep(hours));
			boolean partial = false;
			for (WeatherVO w : grids.values()) if (w.isPartial()) partial = true;
			o.addProperty("partial", partial);
			o.addProperty("tempNote", "");
			List<String[]> skies = new ArrayList<>();
			JsonObject gs = new JsonObject();
			for (Map.Entry<String, WeatherVO> e : grids.entrySet()) {
				WeatherVO w = e.getValue();
				Map<Integer, HourVO> byHour = new LinkedHashMap<>();
				for (HourVO h : w.getHours()) byHour.put(h.getHourOfDay(), h);
				JsonObject g = new JsonObject();
				g.addProperty("nx", w.getNx());
				g.addProperty("ny", w.getNy());
				JsonArray t = new JsonArray(), p = new JsonArray(), sk = new JsonArray();
				for (int hr : hours) {
					HourVO h = byHour.get(hr);
					if (h == null) {
						addNum(t, null);
						addNum(p, null);
						addNum(sk, null);
					} else {
						addNum(t, h.getTempValue());
						addNum(p, h.getPop());
						addNum(sk, skyIndex(skies, h.getSky(), h.getIcon()));
					}
				}
				g.add("t", t);
				g.add("p", p);
				g.add("s", sk);
				gs.add(e.getKey(), g);
			}
			o.add("skies", skiesJson(skies));
			o.add("grids", gs);
			return o;
		}

		// 처음에 보여 줄 칸: 오늘이면 지금 시 이후 첫 칸(없으면 마지막), 다른 날은 12시(없으면 가장 가까운 시)
		private int defaultStep(List<Integer> hours) {
			if (day == 0) {
				for (int i = 0; i < hours.size(); i++) if (hours.get(i) >= nowHour) return i;
				return hours.size() - 1;
			}
			int best = 0;
			for (int i = 0; i < hours.size(); i++) {
				if (Math.abs(hours.get(i) - 12) < Math.abs(hours.get(best) - 12)) best = i;
			}
			return best;
		}

		private static JsonObject none() {
			JsonObject o = new JsonObject();
			o.addProperty("mode", "none");
			return o;
		}

		private static void addNum(JsonArray a, Number n) {
			if (n == null) a.add(JsonNull.INSTANCE); else a.add(n);
		}

		// 하늘 이름표 번호 (같은 하늘은 한 번만 적는다). 이름이 없으면 null
		private static Integer skyIndex(List<String[]> skies, String name, String icon) {
			if (name == null || name.isEmpty()) return null;
			for (int i = 0; i < skies.size(); i++) if (skies.get(i)[0].equals(name)) return i;
			skies.add(new String[] { name, icon == null ? "" : icon });
			return skies.size() - 1;
		}

		private static JsonArray skiesJson(List<String[]> skies) {
			JsonArray a = new JsonArray();
			for (String[] s : skies) {
				JsonArray x = new JsonArray();
				x.add(s[0]);
				x.add(s[1]);
				a.add(x);
			}
			return a;
		}
	}
}
