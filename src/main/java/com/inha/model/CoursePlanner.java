package com.inha.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// 날씨 맞춤 하루 코스 (핵심 융합 기능)
//  1) 고른 날의 시간대별 예보를 2시간 블록(10-12 · 12-14 · 14-16 · 16-18 · 18-20)으로 나눠 실외 좋음 / 실내 권장을 가른다
//     (중기예보 날은 시간별 예보가 없어서 오전 · 오후 2칸으로만 짠다)
//  2) 블록마다 한 곳씩: 그 관광지 자기 자리 날씨가 좋으면 실외 관광지, 나쁘면 실내 관광지 (모자라면 다른 쪽으로 채우고 표시)
//     가운데 날씨(cw)는 어떤 블록이 있는지(예보가 있는 시간대)만 정한다
//  3) 동선은 하버사인 직선거리 최근접 이웃. 시작은 관광지 좌표 가운데에서 가장 가까운 곳. 한 곳은 한 번만
//  4) 일몰(천문연 출몰시각): 해가 지기 전에 실외 칸을 끝낸다.
//     - 일몰 직전 칸(SUNSET)은 해변 · 전망대 · 공원 · 해수욕장 · 타워 · 다리 같은 노을 명소를 먼저 고른다.
//     - 해 진 뒤 칸(NIGHT)은 실내 관광지나 야경 명소만 고른다.
//  5) 비 오면 플랜 B: 실외 칸마다 이미 받아 둔 목록(spots)에서 가장 가까운 실내 관광지(5km 안)를 대안으로 붙인다.
//     추가 호출은 없고 하버사인 거리만 잰다. 그 칸이 비 위험(PTY>0 또는 강수확률 30% 이상)이면 화면이 바로 보여 주고, 아니면 접어 둔다.
public class CoursePlanner {

	private static final int[] STARTS = { 10, 12, 14, 16, 18 }; // 블록 시작 시각 (마지막 블록은 18-20)
	private static final double RADIUS_KM = 30;                  // 하루에 다닐 수 있는 범위(시작 곳 기준)
	private static final int MIN_POOL = 4;                       // 범위 안에 이보다 적으면 가까운 순으로 넓힌다
	private static final int WIDE_POOL = 8;
	private static final double SUNSET_EXTRA_KM = 8;            // 노을 명소를 고르려고 더 가도 되는 거리
	private static final double ALT_KM = 5;                      // 비 오면 플랜 B: 실외 칸 옆 실내 대안을 찾는 반경
	private static final int RAIN_RISK_POP = 30;                 // 비 위험: 강수형태가 있거나 강수확률이 이 값 이상

	// 이름에 이런 말이 들어간 곳은 노을을 보기 좋은 실외 명소로 본다
	private static final String[] SUNSET_WORDS = { "해변", "전망대", "공원", "해수욕장", "타워", "다리" };
	// 해 진 뒤에도 갈 만한 실외 명소 (야경)
	private static final String[] NIGHT_WORDS = { "야경", "야시장", "야간", "타워", "전망대", "다리", "분수" };

	// 해 지는 시각을 모를 때(예전 호출 · 시험)의 짧은 길
	public static DayPlanVO plan(List<TourVO> spots, double[] center, WeatherVO cw, int day, LocalTime now) {
		return plan(spots, center, cw, day, now, null, "");
	}

	// spots: 날씨(own weather)가 붙은 관광지들, center: 관광지 좌표의 가운데 {위도, 경도},
	// cw: 가운데 지점의 하루 예보(블록 판정용), now: 오늘이면 이 시각 이후 블록만 쓴다
	// fc: 그 날 이어 붙인 예보 (중기예보 날이면 오전 · 오후 2칸으로 짠다, 아니면 null 가능)
	// sunsetClock: 그 날 해 지는 시각 "17:14" (모르면 "")
	public static DayPlanVO plan(List<TourVO> spots, double[] center, WeatherVO cw, int day, LocalTime now,
			DayForecastVO fc, String sunsetClock) {
		DayPlanVO plan = new DayPlanVO();
		plan.setNote("");
		plan.setMessage("");
		plan.setSunset(sunsetClock == null ? "" : sunsetClock);
		int sunsetMin = SunVO.minutes(plan.getSunset());
		boolean mid = fc != null && DayForecastVO.MID.equals(fc.getSource());
		plan.setMid(mid);
		if (center == null || cw == null) {
			plan.setMessage("좌표나 날씨 정보가 없어서 하루 코스를 짜지 못했어요.");
			return plan;
		}

		// ① 쓸 수 있는 블록 고르기
		List<PlanBlockVO> blocks = mid ? midBlocks(fc) : hourBlocks(cw, day, now);
		if (blocks.size() < 2) {
			if (day == 0) {
				plan.setTooLate(true);
				plan.setMessage("오늘 남은 시간이 짧아요. 내일 코스를 확인해 보세요.");
			} else {
				plan.setMessage("이날은 시간대별 예보가 부족해서 하루 코스를 짜지 못했어요.");
			}
			return plan;
		}
		markPhases(blocks, sunsetMin);

		// ② 후보: 좌표가 있는 관광지. 시작 곳(가운데에서 가장 가까운 곳) 기준 30km 안
		List<TourVO> pool = new ArrayList<>();
		for (TourVO v : spots) {
			if (v.isHasLocation()) pool.add(v);
		}
		if (pool.size() < 2) {
			plan.setMessage("하루 코스를 짜려면 좌표가 있는 관광지가 2곳 이상 필요해요.");
			return plan;
		}
		TourVO anchor = nearest(pool, center[0], center[1], null);
		List<TourVO> near = new ArrayList<>();
		for (TourVO v : pool) {
			if (Geo.distanceKm(anchor.getLat(), anchor.getLon(), v.getLat(), v.getLon()) <= RADIUS_KM) near.add(v);
		}
		String note = "";
		if (near.size() < pool.size()) note = "넓은 지역이라 가운데에서 " + (int) RADIUS_KM + "km 안의 곳으로 짰어요.";
		if (near.size() < MIN_POOL) {
			// 범위 안에 너무 적으면 가까운 순으로 몇 곳 더 (이 경우 멀리 이동할 수 있다)
			near = new ArrayList<>(pool);
			near.sort((x, y) -> Double.compare(
					Geo.distanceKm(anchor.getLat(), anchor.getLon(), x.getLat(), x.getLon()),
					Geo.distanceKm(anchor.getLat(), anchor.getLon(), y.getLat(), y.getLon())));
			if (near.size() > WIDE_POOL) near = new ArrayList<>(near.subList(0, WIDE_POOL));
			note = "가까운 곳이 적어서 조금 먼 곳도 넣었어요.";
		}

		// ③ 최근접 이웃: 첫 블록은 가운데에서, 그 뒤는 직전에 고른 곳에서 가장 가까운 곳
		//    실내/실외 판정은 가운데가 아니라 후보 관광지 자기 자리의 날씨로 한다
		//    (그 시간 자기 날씨가 야외에 좋으면 실외 관광지, 아니면 실내 관광지가 맞는 후보)
		List<TourVO> used = new ArrayList<>();
		List<PlanBlockVO> filled = new ArrayList<>();
		double curLat = center[0], curLon = center[1];
		for (PlanBlockVO b : blocks) {
			TourVO fitBest = null, prefBest = null, anyBest = null;
			double fitKm = Double.MAX_VALUE, prefKm = Double.MAX_VALUE, anyKm = Double.MAX_VALUE;
			for (TourVO v : near) {
				if (used.contains(v)) continue;
				boolean ownGood = ownProblem(v, cw, b).isEmpty();
				double km = Geo.distanceKm(curLat, curLon, v.getLat(), v.getLon());
				boolean fits;
				if (b.isNight()) fits = v.isIndoor() || hasWord(v.getTitle(), NIGHT_WORDS); // 해 진 뒤: 실내 또는 야경
				else fits = v.isIndoor() != ownGood;                                         // 낮: 날씨에 맞는 종류
				if (fits && km < fitKm) { fitKm = km; fitBest = v; }
				// 일몰 직전 칸이고 날씨가 좋으면 노을 명소를 먼저
				if (b.isSunsetBlock() && !v.isIndoor() && ownGood && hasWord(v.getTitle(), SUNSET_WORDS) && km < prefKm) {
					prefKm = km;
					prefBest = v;
				}
				if (km < anyKm) { anyKm = km; anyBest = v; } // 종류 상관없이 가장 가까운 곳
			}
			boolean usePref = prefBest != null && (fitBest == null || prefKm <= fitKm + SUNSET_EXTRA_KM);
			boolean fallback = !usePref && fitBest == null;
			TourVO pick = usePref ? prefBest : (fallback ? anyBest : fitBest); // 원하는 종류가 없으면 다른 종류로 채운다
			if (pick == null) break; // 관광지가 모자라면 여기까지
			used.add(pick);
			curLat = pick.getLat();
			curLon = pick.getLon();
			// 칸의 판정 · 이유는 고른 곳 자기 날씨 기준
			String problem = ownProblem(pick, cw, b);
			b.setGood(problem.isEmpty());
			b.setKind(problem.isEmpty() ? "실외 좋음" : "실내 권장");
			b.setReason(problem);
			if (b.isNight()) { // 해 진 뒤 칸은 날씨 판정 대신 실내 · 야경 칸으로 부른다
				b.setGood(pick.isIndoor() || problem.isEmpty());
				b.setKind(nightKind(pick));
				b.setReason(pick.isIndoor() ? "" : problem);
			}
			b.setSpot(pick);
			b.setFallback(fallback);
			filled.add(b);
		}
		if (filled.size() < 2) {
			plan.setMessage("하루 코스를 짤 만큼 관광지가 모자라요.");
			return plan;
		}

		// ④ 각 칸에 그 곳 자기 격자의 그 시간대 날씨 붙이기, 다음 곳까지 거리, 일몰 안내
		double total = 0;
		StringBuilder ids = new StringBuilder();
		List<TourVO> altUsed = new ArrayList<>(); // 이미 대안으로 쓴 곳 (가능하면 서로 다른 대안을 준다)
		for (int i = 0; i < filled.size(); i++) {
			PlanBlockVO b = filled.get(i);
			TourVO v = b.getSpot();
			b.setNo(i + 1);
			b.setLast(i == filled.size() - 1);
			if (b.isMid()) summarizeMid(b, fc);
			else summarize(b, ownHours(v, cw, b.getStartHour()));
			b.setApprox(!b.isMid() && (v.getWeather() == null || v.isWeatherApprox()));
			b.setRainRisk(rainRiskOf(b, v, cw));
			if (!v.isIndoor()) attachAlt(b, v, spots, filled, altUsed);
			b.setWarn("");
			String own = ownProblem(v, cw, b);
			if (!v.isIndoor() && !own.isEmpty() && !b.isNight()) {
				b.setWarn("이 곳은 이 시간에 " + own + " 예보예요. 우산을 챙기거나 실내와 순서를 바꿔 보세요.");
			}
			applySunset(b, v, sunsetMin, plan.getSunset());
			if (i + 1 < filled.size()) {
				TourVO nx = filled.get(i + 1).getSpot();
				double km = Geo.distanceKm(v.getLat(), v.getLon(), nx.getLat(), nx.getLon());
				total += km;
				b.setNextKm(Math.round(km * 10) / 10.0);
			}
			if (i > 0) ids.append(',');
			ids.append(v.getContentId());
		}
		boolean anyFallback = false;
		for (PlanBlockVO b : filled) if (b.isFallback()) anyFallback = true;
		if (anyFallback) note = (note.isEmpty() ? "" : note + " ") + "실내/실외 한쪽이 모자라서 다른 쪽으로 채운 칸이 있어요.";
		if (mid) note = (note.isEmpty() ? "" : note + " ") + "중기예보라 시간별 예보가 없어서 오전 · 오후 두 칸으로만 짰어요.";

		plan.setAvailable(true);
		plan.setBlocks(filled);
		plan.setTotalKm(Math.round(total * 10) / 10.0);
		plan.setIds(ids.toString());
		plan.setNote(note);
		return plan;
	}

	// 시간별 예보가 있는 날의 블록들 (오늘이면 지금 이후만, 예보가 없는 시간대는 뺀다)
	private static List<PlanBlockVO> hourBlocks(WeatherVO cw, int day, LocalTime now) {
		int minHour = day == 0 ? now.getHour() + (now.getMinute() > 0 ? 1 : 0) : 0;
		List<PlanBlockVO> blocks = new ArrayList<>();
		for (int s : STARTS) {
			if (s < minHour) continue;
			List<HourVO> hs = hoursOf(cw, s);
			if (hs.isEmpty()) continue;
			PlanBlockVO b = new PlanBlockVO();
			b.setStartHour(s);
			b.setEndHour(s + 2);
			b.setTimeLabel(String.format("%02d:00–%02d:00", s, s + 2));
			String problem = problemOf(hs);
			b.setGood(problem.isEmpty());
			b.setKind(problem.isEmpty() ? "실외 좋음" : "실내 권장");
			b.setReason(problem);
			blocks.add(b);
		}
		return blocks;
	}

	// 중기예보 날의 블록 2개: 오전 09-12, 오후 13-17. 오전 · 오후 하늘과 강수확률로 판정한다
	private static List<PlanBlockVO> midBlocks(DayForecastVO fc) {
		List<PlanBlockVO> blocks = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			boolean am = i == 0;
			PlanBlockVO b = new PlanBlockVO();
			b.setMid(true);
			b.setPeriod(am ? "오전" : "오후");
			b.setStartHour(am ? 9 : 13);
			b.setEndHour(am ? 12 : 17);
			b.setTimeLabel(am ? "09:00–12:00" : "13:00–17:00");
			String problem = midProblemOf(am ? fc.getAmSky() : fc.getPmSky(), am ? fc.getAmPop() : fc.getPmPop(), fc.getMaxTemp());
			b.setMidProblem(problem);
			b.setGood(problem.isEmpty());
			b.setKind(problem.isEmpty() ? "실외 좋음" : "실내 권장");
			b.setReason(problem);
			blocks.add(b);
		}
		return blocks;
	}

	// 중기 칸이 야외에 좋은가: 비 · 눈 하늘, 강수확률 40% 이상, 최고기온 1° 미만 · 32° 초과면 나쁨 (시간별 판정과 같은 기준)
	private static String midProblemOf(String sky, int pop, Integer maxTemp) {
		if ("비".equals(sky) || "비/눈".equals(sky) || "눈".equals(sky) || "소나기".equals(sky)) return sky;
		if (pop >= 40) return "강수확률 " + pop + "%";
		if (maxTemp != null && maxTemp < 1) return "기온 " + maxTemp + "° (추움)";
		if (maxTemp != null && maxTemp > 32) return "기온 " + maxTemp + "° (더움)";
		return "";
	}

	// 블록마다 해 있는 시간(DAY) · 일몰이 걸친 칸(SUNSET) · 해 진 뒤(NIGHT)를 정한다
	// 일몰이 걸친 칸이 없으면, 일몰 90분 안에 끝나는 마지막 낮 칸을 일몰 직전 칸으로 본다
	private static void markPhases(List<PlanBlockVO> blocks, int sunsetMin) {
		if (sunsetMin < 0) return; // 해 지는 시각을 모르면 낮으로 본다
		PlanBlockVO lastDay = null;
		boolean hasSunsetBlock = false;
		for (PlanBlockVO b : blocks) {
			int startMin = b.getStartHour() * 60, endMin = b.getEndHour() * 60;
			if (startMin >= sunsetMin) {
				b.setPhase("NIGHT");
			} else if (endMin > sunsetMin) {
				b.setPhase("SUNSET");
				hasSunsetBlock = true;
			} else {
				lastDay = b;
			}
		}
		if (!hasSunsetBlock && lastDay != null && sunsetMin - lastDay.getEndHour() * 60 <= 90) {
			lastDay.setPhase("SUNSET");
		}
	}

	// 일몰 안내: 실외 칸이 일몰을 넘기면 일몰 시각까지로 줄이고, 노을 명소면 그렇게 알려 준다
	private static void applySunset(PlanBlockVO b, TourVO v, int sunsetMin, String sunsetClock) {
		b.setSunsetNote("");
		if (sunsetMin < 0) return;
		if (b.isNight()) {
			String what = v.isIndoor() ? "실내" : (hasWord(v.getTitle(), NIGHT_WORDS) ? "야경 명소" : "실외 (실내 · 야경 명소가 모자라요)");
			b.setSunsetNote("일몰 " + sunsetClock + " 이후라 " + what + "로 잡았어요.");
			return;
		}
		if (!b.isSunsetBlock()) return;
		boolean sunsetSpot = !v.isIndoor() && b.isGood() && hasWord(v.getTitle(), SUNSET_WORDS); // 날씨가 나쁘면 노을 안내는 안 한다
		String msg = "";
		if (!v.isIndoor() && b.getEndHour() * 60 > sunsetMin) {
			b.setTimeLabel(String.format("%02d:00–%s", b.getStartHour(), sunsetClock));
			b.setSunsetCut(true);
			msg = "해가 지는 " + sunsetClock + " 전에 마치도록 시간을 줄였어요.";
		}
		if (sunsetSpot) msg = "일몰 " + sunsetClock + " 직전 — 노을 보기 좋은 곳이에요. " + msg;
		b.setSunsetNote(msg.trim());
	}

	// 이 칸이 비 위험인가: 그 곳 자기 날씨의 이 시간대에 비 · 눈이 있거나 강수확률이 30% 이상
	private static boolean rainRiskOf(PlanBlockVO b, TourVO v, WeatherVO cw) {
		if (b.isMid()) return b.getPop() >= RAIN_RISK_POP || isRainWord(b.getSky());
		for (HourVO h : ownHours(v, cw, b.getStartHour())) {
			if (h.isRainSky() || h.getPop() >= RAIN_RISK_POP) return true;
		}
		return false;
	}

	private static boolean isRainWord(String sky) {
		return "비".equals(sky) || "비/눈".equals(sky) || "눈".equals(sky) || "소나기".equals(sky);
	}

	// 실외 칸의 플랜 B: 이미 받은 목록에서 가장 가까운 실내 관광지 (5km 안). 코스에 이미 있는 곳은 빼고, 다른 칸에 준 대안은 가능하면 피한다
	static void attachAlt(PlanBlockVO b, TourVO v, List<TourVO> spots, List<PlanBlockVO> course, List<TourVO> altUsed) {
		TourVO best = null, bestFresh = null;
		double km = Double.MAX_VALUE, kmFresh = Double.MAX_VALUE;
		double kmIn = Double.MAX_VALUE;   // 코스에 이미 들어 있는 실내 관광지 중 가장 가까운 곳까지 (대안이 없을 때 그 곳을 알려 주려고)
		for (TourVO c : spots) {
			if (!c.isIndoor() || !c.isHasLocation() || c == v) continue;
			int courseNo = 0;
			for (int j = 0; j < course.size(); j++) {
				if (course.get(j).getSpot() == c) courseNo = j + 1;
			}
			double d = Geo.distanceKm(v.getLat(), v.getLon(), c.getLat(), c.getLon());
			if (courseNo > 0) {
				if (d <= ALT_KM && d < kmIn) {
					kmIn = d;
					b.setInCourseAlt(c);
					b.setInCourseAltNo(courseNo);
					b.setInCourseAltKm(Math.round(d * 10) / 10.0);
				}
				continue;
			}
			if (d > ALT_KM) continue;
			if (d < km) { km = d; best = c; }
			if (!altUsed.contains(c) && d < kmFresh) { kmFresh = d; bestFresh = c; }
		}
		TourVO pick = bestFresh != null ? bestFresh : best;
		if (pick == null) return; // 5km 안에 실내 대안이 없다 → 화면이 「근처 실내 대안 없음」을 보여 준다
		altUsed.add(pick);
		b.setAlt(pick);
		b.setAltKm(Math.round((pick == bestFresh ? kmFresh : km) * 10) / 10.0);
	}

	// 해 진 뒤 칸의 이름
	private static String nightKind(TourVO v) {
		if (v.isIndoor()) return "일몰 후 · 실내";
		return hasWord(v.getTitle(), NIGHT_WORDS) ? "일몰 후 · 야경" : "일몰 후 · 실외";
	}

	private static boolean hasWord(String title, String[] words) {
		if (title == null) return false;
		for (String w : words) {
			if (title.contains(w)) return true;
		}
		return false;
	}

	// 이 곳이 이 칸에서 야외에 좋은지 (나쁘면 이유, 좋으면 ""). 중기 칸은 지역 단위라 모든 곳에 같다
	private static String ownProblem(TourVO v, WeatherVO cw, PlanBlockVO b) {
		if (b.isMid()) return b.getMidProblem();
		return problemOf(ownHours(v, cw, b.getStartHour()));
	}

	// 상세 화면: 이 관광지 날씨로 "언제 가면 좋은지" 한 줄. 실외면 가장 좋은 2시간, 실내면 비 오는 시간대에 딱
	public static String bestTime(TourVO spot, WeatherVO w, int day, LocalTime now) {
		int minHour = day == 0 ? now.getHour() + (now.getMinute() > 0 ? 1 : 0) : 0;
		String bestLabel = null;
		double bestScore = Double.MAX_VALUE;
		String rainLabel = "";
		for (int s : STARTS) {
			if (s < minHour) continue;
			List<HourVO> hs = hoursOf(w, s);
			if (hs.isEmpty()) continue;
			String problem = problemOf(hs);
			String label = String.format("%02d:00–%02d:00", s, s + 2);
			if (problem.isEmpty()) {
				// 좋은 블록 중 강수확률이 낮고 기온이 20도에 가까운 곳
				int pop = 0;
				double tsum = 0;
				int tn = 0;
				for (HourVO h : hs) {
					pop = Math.max(pop, h.getPop());
					if (h.getTempValue() != null) { tsum += h.getTempValue(); tn++; }
				}
				double score = pop + (tn == 0 ? 0 : Math.abs(tsum / tn - 20));
				if (score < bestScore) {
					bestScore = score;
					bestLabel = label;
				}
			} else if (rainLabel.isEmpty() && hs.stream().anyMatch(h -> h.isRainSky() || h.getPop() >= 40)) {
				rainLabel = label;
			}
		}
		if (spot.isIndoor()) {
			if (!rainLabel.isEmpty()) return "비 소식이 있는 " + rainLabel + "에 가면 딱 좋아요. 실내라 비를 피할 수 있어요.";
			return "날씨와 상관없이 언제든 둘러보기 좋은 실내예요.";
		}
		if (bestLabel != null) return "가장 가기 좋은 시간은 " + bestLabel + "예요. 비 걱정이 적고 기온도 무난해요.";
		if (minHour > STARTS[STARTS.length - 1]) return "오늘은 둘러보기엔 늦었어요. 내일 날씨를 확인해 보세요.";
		return "이날은 야외 관광에 좋은 시간대가 없어요. 다른 날을 골라 보세요.";
	}

	// 중기예보 날의 "언제 가면 좋은지": 시간별 예보가 없어서 오전 · 오후 중 어느 쪽이 나은지만 말한다
	public static String bestTimeMid(TourVO spot, DayForecastVO fc) {
		String am = midProblemOf(fc.getAmSky(), fc.getAmPop(), fc.getMaxTemp());
		String pm = midProblemOf(fc.getPmSky(), fc.getPmPop(), fc.getMaxTemp());
		String head = "중기예보라 시간별 예보는 없어요. ";
		if (spot.isIndoor()) {
			if (!am.isEmpty() || !pm.isEmpty()) {
				String when = !am.isEmpty() && !pm.isEmpty() ? "하루 종일" : (!am.isEmpty() ? "오전" : "오후");
				return head + "비나 추위 걱정이 있는 " + when + "에 가면 딱 좋아요. 실내라 날씨를 피할 수 있어요.";
			}
			return head + "날씨와 상관없이 언제든 둘러보기 좋은 실내예요.";
		}
		if (am.isEmpty() && pm.isEmpty()) return head + "오전 · 오후 모두 야외 관광하기 괜찮은 예보예요.";
		if (am.isEmpty()) return head + "오전이 더 나아요. 오후는 " + pm + " 예보가 있어요.";
		if (pm.isEmpty()) return head + "오후가 더 나아요. 오전은 " + am + " 예보가 있어요.";
		return head + "오전 · 오후 모두 " + am + " 예보가 있어서 야외 관광엔 좋지 않아요. 다른 날을 골라 보세요.";
	}

	// 관광지 자기 격자의 그 블록 예보 (자기 날씨가 없거나 그 시간이 비면 가운데 날씨)
	private static List<HourVO> ownHours(TourVO v, WeatherVO cw, int start) {
		List<HourVO> hs = hoursOf(v.getWeather(), start);
		return hs.isEmpty() ? hoursOf(cw, start) : hs;
	}

	// 블록(start 시, start+1 시) 두 시간의 예보 칸. 없는 시각은 빠진다
	private static List<HourVO> hoursOf(WeatherVO w, int start) {
		List<HourVO> list = new ArrayList<>();
		if (w == null) return list;
		for (HourVO h : w.getHours()) {
			int hh = h.getHourOfDay();
			if (hh == start || hh == start + 1) list.add(h);
		}
		return list;
	}

	// 한 시간이 야외 관광에 좋은가: 비 · 눈 없음(PTY=0), 강수확률 40% 미만, 기온 1~32°
	// 나쁘면 이유 문구, 좋으면 ""
	private static String problemOf(HourVO h) {
		if (h.isRainSky()) return h.getSky();
		if (h.getPop() >= 40) return "강수확률 " + h.getPop() + "%";
		Integer t = h.getTempValue();
		if (t != null && t < 1) return "기온 " + t + "° (추움)";
		if (t != null && t > 32) return "기온 " + t + "° (더움)";
		return "";
	}

	// 블록 두 시간 중 하나라도 나쁘면 그 이유 (없으면 "")
	private static String problemOf(List<HourVO> hs) {
		for (HourVO h : hs) {
			String p = problemOf(h);
			if (!p.isEmpty()) return p;
		}
		return "";
	}

	// 칸에 보여 줄 날씨: 기온은 평균, 강수확률은 최대, 아이콘은 비가 오는 시간 > 강수확률 높은 시간
	private static void summarize(PlanBlockVO b, List<HourVO> hs) {
		HourVO shown = hs.get(0);
		int pop = 0;
		double tsum = 0;
		int tn = 0;
		for (HourVO h : hs) {
			pop = Math.max(pop, h.getPop());
			if (h.getTempValue() != null) { tsum += h.getTempValue(); tn++; }
			if (h.isRainSky() && !shown.isRainSky()) shown = h;
			else if (h.isRainSky() == shown.isRainSky() && h.getPop() > shown.getPop()) shown = h;
		}
		b.setIcon(shown.getIcon());
		b.setSky(shown.getSky());
		b.setPop(pop);
		b.setTemp(tn == 0 ? "-" : Math.round(tsum / tn) + "°");
	}

	// 중기 칸의 날씨: 오전 · 오후 하늘과 강수확률, 기온은 그 날 최저~최고
	private static void summarizeMid(PlanBlockVO b, DayForecastVO fc) {
		boolean am = "오전".equals(b.getPeriod());
		b.setIcon(am ? fc.getAmIcon() : fc.getPmIcon());
		b.setSky(am ? fc.getAmSky() : fc.getPmSky());
		b.setPop(Math.max(0, am ? fc.getAmPop() : fc.getPmPop()));
		b.setTemp(fc.getTempRange());
	}

	// list 에서 (lat, lon)에 가장 가까운 곳
	private static TourVO nearest(List<TourVO> list, double lat, double lon, TourVO exclude) {
		TourVO best = null;
		double bestKm = Double.MAX_VALUE;
		for (TourVO v : list) {
			if (v == exclude) continue;
			double d = Geo.distanceKm(lat, lon, v.getLat(), v.getLon());
			if (d < bestKm) {
				bestKm = d;
				best = v;
			}
		}
		return best;
	}
}
