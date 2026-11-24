package com.inha.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 여행 지수: "여기 · 이날 가도 될까?"를 0~100 점수 하나와 근거 칩으로 답한다 (두 API 가 아니라 여덟 API 를 엮는다)
//
// 계산 규칙 (발표 때 이대로 설명하면 된다)
//  1) 100점에서 시작해 근거마다 깎는다. 근거마다 최대로 깎이는 점수(가중치)가 정해져 있다.
//       날씨(비 · 눈 · 기온 · 하늘)  최대 -60   ← 여행에서 가장 큰 변수
//       미세먼지(예보/실시간 등급)   최대 -42
//       붐빔(집중률)                 최대 -15
//       자외선(지수 단계)            최대 -12
//  2) 기상특보는 깎는 게 아니라 "점수 상한"을 낮춘다: 큰 경보(호우 · 태풍 · 폭염 · 한파 · 대설) 30점,
//     그 밖의 경보 45점, 주의보 70점, 예비특보 85점. 실내 관광지는 상한을 15점 올려 준다.
//  3) 실내 관광지는 날씨 영향이 작아서 날씨 감점을 30%만, 미세먼지는 40%만 깎고 자외선은 안 깎는다.
//  4) 자료가 없는 근거는 깎지 않고 칩을 회색 "자료 없음"으로 보여 준다 → 남은 근거로만 계산한다 (ScoreVO.missing 에 빠진 개수).
//     (다만 6번: 다른 날에는 있는 근거가 빠진 날은 그냥 두지 않고 불확실로 표시한다.)
//     근거 5개 중 3개 이상이 없으면(날씨만 있는 날 등) 점수는 90점까지만 주고, 노트에 그렇게 적는다.
//  5) 등급: 85점 이상 최고 · 70 이상 좋음 · 50 이상 보통 · 그 아래 비추.
//  6) 불확실: 이 지역의 다른 날에는 값이 있는 근거(미세먼지 · 자외선 · 붐빔 · 특보)가 이 날은 없으면
//     「불확실 · 신뢰도 낮음」으로 표시하고 점수는 90점까지만 준다 (감점이 빠진 날이 100점 「최고」로 보이지 않게).
//  7) 「가장 좋은 날」은 모든 날에 공통으로 있는 근거(보통 날씨 · 붐빔)만으로 겨룬다 (compareScore).
//     단, 이미 알려진 특보의 점수 상한은 그 날의 나쁜 소식이라 공통 근거가 아니어도 항상 반영한다.
//     비교 점수가 같으면 ① 보이는 점수가 높은 날 ② 근거가 다 있는(불확실하지 않은) 날 ③ 더 빠른 날 순서로 고른다.
public class TravelScore {

	// 근거별 최대 감점
	static final int MAX_WEATHER = 60;
	static final int MAX_DUST = 42;
	static final int MAX_CROWD = 15;
	static final int MAX_UV = 12;
	// 특보별 점수 상한
	static final int CAP_MAJOR = 30;
	static final int CAP_WARNING = 45;
	static final int CAP_ADVISORY = 70;
	static final int CAP_PRELIM = 85;
	static final int INDOOR_CAP_BONUS = 15;
	// 「실외 위주」 추천을 낼 수 있는 날씨 감점 상한 (먼지 · 자외선 · 붐빔이 보통 수준이어도 70점 이상이 되게 18)
	public static final int OUTDOOR_MAX_PENALTY = 18;
	// 등급 경계
	static final int BEST = 85;
	static final int GOOD = 70;
	static final int OK = 50;
	// 다른 날에는 있던 근거가 빠진 날의 점수 상한 (자료가 모자란 날이 「최고 100점」으로 보이지 않게)
	static final int UNCERTAIN_CAP = 90;
	// 근거 5개 중 이만큼 이상 자료가 없으면 점수 상한 (날씨만 보고 「최고」를 주지 않는다)
	static final int SPARSE_MISSING = 3;

	// 한 곳 · 한 날의 점수.
	//  ev: 그 지역의 새 API 자료 묶음 (null 이면 새 근거는 모두 "자료 없음")
	//  day: 오늘부터 며칠 뒤(0~10)   w: 그 자리 날씨 (null 이면 날씨 근거 없음)
	//  indoor: 실내 관광지인가   spotCrowd: 이 관광지 이름으로 맞춘 붐빔 값 (없으면 null → 시군구 평균을 쓴다)
	public static ScoreVO score(RegionEvidence ev, int day, WeatherVO w, boolean indoor, Double spotCrowd) {
		List<ChipVO> chips = new ArrayList<>();
		int deduct = 0;

		// ① 날씨
		ChipVO c = weatherChip(w, indoor);
		chips.add(c);
		deduct += -c.getDelta();

		// ② 미세먼지
		c = dustChip(ev, day, indoor);
		chips.add(c);
		deduct += -c.getDelta();

		// ③ 자외선
		c = uvChip(ev, day, indoor);
		chips.add(c);
		deduct += -c.getDelta();

		// ④ 붐빔
		c = crowdChip(ev, day, spotCrowd);
		chips.add(c);
		deduct += -c.getDelta();

		// ⑤ 특보: 감점이 아니라 상한 (상한은 칩이 들고 있다)
		c = warnChip(ev, day, indoor);
		chips.add(c);
		int cap = c.getCap();

		int missing = 0;
		for (ChipVO x : chips) if (x.isMissing()) missing++;

		ScoreVO s = new ScoreVO();
		s.setChips(chips);
		s.setMissing(missing);
		s.setCap(cap);
		if (missing == chips.size()) { // 근거가 하나도 없으면 점수를 내지 않는다
			s.setAvailable(false);
			s.setGrade("자료 없음");
			s.setGradeKey("none");
			s.setNote("여행 지수를 만들 자료가 없어요.");
			return s;
		}
		int raw = Math.max(0, 100 - deduct);
		int score = Math.min(raw, cap);
		s.setRawScore(score);
		boolean sparse = missing >= SPARSE_MISSING;
		boolean sparseCut = sparse && score > UNCERTAIN_CAP; // 자료가 모자라서 실제로 깎였나
		if (sparse) score = Math.min(score, UNCERTAIN_CAP);
		// 불확실: 이 지역에서 다른 날에는 값이 있는 근거가 이 날은 빠졌나
		List<String> lacking = lackingLabels(ev, chips);
		if (!lacking.isEmpty()) {
			s.setUncertain(true);
			s.setUncertainNote((day >= 4 ? "중기예보 · " : "") + String.join(" · ", lacking) + " 빠짐");
			score = Math.min(score, UNCERTAIN_CAP);
		}
		s.setAvailable(true);
		s.setScore(score);
		s.setGrade(gradeOf(score));
		s.setGradeKey(gradeKeyOf(score));
		StringBuilder note = new StringBuilder();
		if (cap < raw) note.append("기상특보 때문에 최대 ").append(cap).append("점으로 제한했어요. ");
		if (missing > 0) {
			note.append("근거 ").append(chips.size()).append("개 중 ").append(chips.size() - missing).append("개로 계산했어요.");
			if (w == null) note.append(" 날씨 자료가 없어서 정확도가 낮아요.");
			if (sparseCut) note.append(" 자료가 모자라 ").append(UNCERTAIN_CAP).append("점까지만 줘요.");
		}
		if (s.isUncertain()) {
			note.append(" 다른 날에는 있는 ").append(String.join(" · ", lacking)).append(" 자료가 이날은 아직 없어서 신뢰도가 낮아요(점수는 ")
					.append(UNCERTAIN_CAP).append("점까지만).");
		}
		s.setNote(note.toString().trim());
		return s;
	}

	// 이 지역에서 어느 날이든 값이 한 번이라도 나오는 근거 이름들 (미세먼지 · 자외선 · 붐빔 · 특보). 지역마다 한 번만 계산해 둔다
	static Set<String> horizonKeys(RegionEvidence ev) {
		Set<String> keys = ev.getHorizonKeys();
		if (keys != null) return keys;
		keys = new LinkedHashSet<>();
		for (int d = 0; d <= Area.MAX_DAY; d++) {
			ChipVO[] chips = { dustChip(ev, d, false), uvChip(ev, d, false), crowdChip(ev, d, null), warnChip(ev, d, false) };
			for (ChipVO c : chips) {
				if (!c.isMissing()) keys.add(c.getKey());
			}
		}
		ev.setHorizonKeys(keys);
		return keys;
	}

	// 이 날 빠진 근거 중 다른 날에는 있는 것의 화면 이름들
	private static List<String> lackingLabels(RegionEvidence ev, List<ChipVO> chips) {
		List<String> list = new ArrayList<>();
		if (ev == null) return list;
		Set<String> ref = horizonKeys(ev);
		for (ChipVO c : chips) {
			if (c.isMissing() && ref.contains(c.getKey())) list.add(c.getLabel());
		}
		return list;
	}

	public static String gradeOf(int score) {
		if (score >= BEST) return "최고";
		if (score >= GOOD) return "좋음";
		if (score >= OK) return "보통";
		return "비추";
	}

	public static String gradeKeyOf(int score) {
		if (score >= BEST) return "best";
		if (score >= GOOD) return "good";
		if (score >= OK) return "ok";
		return "bad";
	}

	// 깎인 점수 → 칩 색 (0~-4 good, -5~-14 ok, -15 이하 bad)
	private static String levelOf(int delta) {
		if (delta > -5) return ChipVO.GOOD;
		if (delta > -15) return ChipVO.OK;
		return ChipVO.BAD;
	}

	// ---- ① 날씨 ----
	// 비 · 눈 예보(또는 강수확률 60% 이상) -56, 강수확률 50% -30, 40% -20, 30% -12, 20% -3
	// 최고기온: 쾌적 17~26° 는 0. 아래 점을 직선으로 이어서 1° 마다 조금씩 변한다 (계단 없음)
	//          추위 14° -12, 10° -20, 5° -30, 0° -46, -5° 이하 -60 / 더위 30° -16, 33° -36, 36° -48, 40° 이상 -60
	// 최저기온: 6° 이상 0, 0° -3, -5° -5, -10° 이하 -8 (직선으로 이음)
	// 비가 없으면 하늘: 흐림 -6, 구름많음 -3. 합이 60점을 넘지 않게 자른다. 실내는 30%만.
	private static ChipVO weatherChip(WeatherVO w, boolean indoor) {
		if (w == null) return new ChipVO("weather", "날씨", "자료 없음", ChipVO.NONE, 0, "이날 날씨 예보를 못 구했어요.");
		int p = weatherPenalty(w);
		if (indoor) p = Math.round(p * 0.3f); // 실내는 날씨 영향이 작다
		String text = w.getSky() + " · " + w.getTempRange() + " · 강수확률 " + w.getMaxPop() + "%";
		String hint = indoor ? "실내 관광지라 날씨 감점을 30%만 반영했어요." : "비 · 기온 · 하늘을 보고 최대 60점까지 깎아요.";
		if (w.isMaxFromHours()) hint += " 오늘은 남은 시간의 기온 기준이에요.";
		return new ChipVO("weather", "날씨", text, levelOf(-p), -p, hint);
	}

	// 날씨 감점(실외 기준, 0~60). 추천 방향(Recommender)도 이 값을 쓴다
	public static int weatherPenalty(WeatherVO w) {
		int p = 0;
		if (w.isRainy()) p += 56;
		else p += popPenalty(w.getMaxPop());
		int max = w.getMaxTemp() == null ? 20 : w.getMaxTemp();
		p += maxTempPenalty(max);
		if (w.getMinTemp() != null) p += minTempPenalty(w.getMinTemp());
		if (!w.isRainy()) {
			if ("흐림".equals(w.getSky())) p += 6;
			else if ("구름많음".equals(w.getSky())) p += 3;
		}
		return Math.min(p, MAX_WEATHER);
	}

	// 점(기온, 감점)들을 직선으로 이은 값. 양 끝 밖은 끝 값을 그대로 쓴다
	private static int lerp(int x, int[][] pts) {
		if (x <= pts[0][0]) return pts[0][1];
		for (int i = 1; i < pts.length; i++) {
			if (x <= pts[i][0]) {
				int x0 = pts[i - 1][0], y0 = pts[i - 1][1], x1 = pts[i][0], y1 = pts[i][1];
				return (int) Math.round(y0 + (double) (y1 - y0) * (x - x0) / (x1 - x0));
			}
		}
		return pts[pts.length - 1][1];
	}

	// 최고기온 점 (기온, 감점): 17~26° 는 0
	private static final int[][] MAX_TEMP_PTS = { { -5, 60 }, { 0, 46 }, { 5, 30 }, { 10, 20 }, { 14, 12 }, { 17, 0 },
			{ 26, 0 }, { 30, 16 }, { 33, 36 }, { 36, 48 }, { 40, 60 } };
	// 최저기온 점 (기온, 감점)
	private static final int[][] MIN_TEMP_PTS = { { -10, 8 }, { -5, 5 }, { 0, 3 }, { 6, 0 } };

	// 강수확률(비 판정 아님) 감점
	static int popPenalty(int pop) {
		if (pop >= 50) return 30;
		if (pop >= 40) return 20;
		if (pop >= 30) return 12;
		if (pop >= 20) return 3;
		return 0;
	}

	// 최고기온 감점 (쾌적 17~26°). 1° 마다 최대 7점
	static int maxTempPenalty(int max) {
		return lerp(max, MAX_TEMP_PTS);
	}

	// 최저기온 감점 (아침 · 밤이 많이 추우면 조금 더)
	static int minTempPenalty(int min) {
		return lerp(min, MIN_TEMP_PTS);
	}

	// ---- ② 미세먼지 ----
	// 등급 보통 -5, 나쁨 -22, 매우나쁨 -42 (실내는 40%)
	private static ChipVO dustChip(RegionEvidence ev, int day, boolean indoor) {
		RegionEvidence.AirView a = ev == null ? null : ev.airView(day);
		if (a == null) {
			return new ChipVO("dust", "미세먼지", "자료 없음", ChipVO.NONE, 0, ev == null ? "미세먼지 자료를 못 구했어요." : ev.airMissingReason(day));
		}
		int p = a.getGrade() >= 4 ? MAX_DUST : a.getGrade() == 3 ? 22 : a.getGrade() == 2 ? 5 : 0;
		if (indoor) p = Math.round(p * 0.4f);
		String text = a.getGradeText() + " (" + a.getSource() + ")";
		String hint = "PM10 " + a.getPm10Text() + " · PM2.5 " + a.getPm25Text() + (a.getTime().isEmpty() ? "" : " · " + a.getTime());
		return new ChipVO("dust", "미세먼지", text, levelOf(-p), -p, hint);
	}

	// ---- ③ 자외선 ----
	// 단계 높음 -3, 매우높음 -7, 위험 -12 (실내는 깎지 않음)
	private static ChipVO uvChip(RegionEvidence ev, int day, boolean indoor) {
		RegionEvidence.UvView u = ev == null ? null : ev.uvView(day);
		if (u == null) {
			return new ChipVO("uv", "자외선", "자료 없음", ChipVO.NONE, 0, ev == null ? "자외선 자료를 못 구했어요." : ev.uvMissingReason(day));
		}
		int p = u.getLevel() >= 5 ? MAX_UV : u.getLevel() == 4 ? 7 : u.getLevel() == 3 ? 3 : 0;
		if (indoor) p = 0;
		return new ChipVO("uv", "자외선", "지수 " + u.getMax() + " · " + u.getLevelText(), levelOf(-p), -p, u.getAdvice());
	}

	// ---- ④ 붐빔 ----
	// 집중률 80% 초과 -15, 60% 초과 -9, 40% 초과 -3. 이 관광지 이름으로 맞는 값이 있으면 그것, 없으면 시군구 평균
	private static ChipVO crowdChip(RegionEvidence ev, int day, Double spotCrowd) {
		boolean own = spotCrowd != null;
		Double rate = own ? spotCrowd : (ev == null ? null : ev.crowdAverage(day));
		if (rate == null) {
			return new ChipVO("crowd", "붐빔", "자료 없음", ChipVO.NONE, 0, ev == null ? "붐빔 자료를 못 구했어요." : ev.crowdMissingReason());
		}
		int p = rate > 80 ? MAX_CROWD : rate > 60 ? 9 : rate > 40 ? 3 : 0;
		String text = Math.round(rate) + "% (" + CrowdVO.levelText(rate) + ")"; // 칩 이름이 이미 「붐빔」이라 앞에 또 붙이지 않는다
		String hint = own ? "이 관광지의 집중률 예측이에요." : "시·군·구 관광지 평균 집중률이에요.";
		return new ChipVO("crowd", "붐빔", text, levelOf(-p), -p, hint);
	}

	// ---- ⑤ 특보 ----
	// 해당하는 특보 중 가장 낮은 상한을 쓴다. 실내는 상한 +15
	// 현재 특보는 오늘 · 내일, 예비특보는 모레까지(그 날 발효 예정인 것만) 반영한다. 그 뒤는 아직 발표 전이라 「없음」이 아니라 「미정」이다
	private static ChipVO warnChip(RegionEvidence ev, int day, boolean indoor) {
		if (ev == null || !ev.getWarningError().isEmpty()) {
			return new ChipVO("warn", "특보", "자료 없음", ChipVO.NONE, 0, ev == null ? "특보 자료를 못 구했어요." : ev.getWarningError());
		}
		List<WarningItem> list = ev.warningsFor(day);
		if (list.isEmpty()) {
			boolean nowActive = !ev.hereCurrent().isEmpty(); // 지금 발효 중인 특보가 있나
			if (day >= 3) {
				String why = "기상특보는 모레까지만 발표돼요." + (nowActive ? " 지금 발효 중인 특보가 있지만 그 날까지 이어질지는 알 수 없어요." : "");
				return new ChipVO("warn", "특보", "미발표", ChipVO.NONE, 0, why);
			}
			if (day == 2 && nowActive) {
				return new ChipVO("warn", "특보", "고른 날 특보 미정", ChipVO.NONE, 0, "지금 발효 중인 특보가 있지만 모레까지 이어질지는 아직 알 수 없어요. 모레에 예고된 특보는 없어요.");
			}
			return new ChipVO("warn", "특보", "없음", ChipVO.GOOD, 0, "이날 이 지역에 발효 중이거나 예고된 특보가 없어요.");
		}
		int cap = 100;
		for (WarningItem w : list) {
			int c = w.isMajor() ? CAP_MAJOR : w.isWarning() ? CAP_WARNING : w.isPrelim() ? CAP_PRELIM : CAP_ADVISORY;
			cap = Math.min(cap, c);
		}
		if (indoor && cap < 100) cap = Math.min(100, cap + INDOOR_CAP_BONUS);
		String text = list.get(0).getKind() + (list.size() > 1 ? " 외 " + (list.size() - 1) + "건" : "");
		String hint = list.get(0).getText();
		return new ChipVO("warn", "특보", text, cap <= CAP_WARNING ? ChipVO.BAD : ChipVO.OK, hint, cap);
	}

	// ---- 날씨 달력(가장 좋은 날 띠) ----
	// 0~10일 뒤 하루마다 지역 점수를 낸다. 「가장 좋은 날」은 모든 날에 공통으로 있는 근거만으로 비교한다 (위 7번 규칙).
	// 비교 점수가 같으면 보이는 점수가 높은 날 → 근거가 다 있는(불확실하지 않은) 날 → 더 빠른 날.
	// selectedDay: 지금 보고 있는 날
	public static List<DayScoreVO> dayStrip(TenDayVO ten, RegionEvidence ev, int selectedDay) {
		List<DayScoreVO> list = new ArrayList<>();
		if (ten == null) return list;
		List<String[]> names = Area.dayOptions();
		for (DayForecastVO d : ten.getDays()) {
			DayScoreVO v = new DayScoreVO();
			int n = d.getDayOffset();
			v.setDayOffset(n);
			v.setDateLabel(d.getDateLabel());
			v.setDayName(names.get(n)[1].split(" ")[0]);
			v.setSource(d.getSource());
			v.setIcon(d.getIcon());
			v.setSky(d.getSky());
			v.setTempRange(d.getTempRange());
			v.setMaxPop(d.getMaxPop());
			v.setNote(d.getNote());
			v.setSelected(n == selectedDay);
			// 날씨가 없는 날은 다른 근거만으로 비교하면 엉뚱한 날이 뽑히므로 점수를 내지 않는다
			if (d.isAvailable() && d.getWeather() != null) {
				v.setScore(score(ev, n, d.getWeather(), false, null));
			} else {
				ScoreVO none = new ScoreVO();
				none.setGrade("자료 없음");
				none.setNote(d.getNote());
				v.setScore(none);
			}
			list.add(v);
		}

		// 모든 (점수가 있는) 날에 값이 있는 근거만 남긴다
		Set<String> common = new LinkedHashSet<>(List.of("weather", "dust", "uv", "crowd", "warn"));
		for (DayScoreVO v : list) {
			if (!v.isHasScore()) continue;
			for (ChipVO c : v.getScore().getChips()) {
				if (c.isMissing()) common.remove(c.getKey());
			}
		}
		StringBuilder basis = new StringBuilder();
		for (String k : common) {
			if (basis.length() > 0) basis.append(" · ");
			basis.append(labelOf(k));
		}
		int bestIdx = -1;
		for (int i = 0; i < list.size(); i++) {
			DayScoreVO v = list.get(i);
			if (!v.isHasScore()) continue;
			v.setCompareScore(compareValue(v.getScore(), common));
			v.setCompareBasis(basis.toString());
			if (bestIdx < 0) {
				bestIdx = i;
				continue;
			}
			DayScoreVO b = list.get(bestIdx);
			// 비교 점수가 같으면 ① 보이는 점수가 높은 날 ② 근거가 다 있는(불확실하지 않은) 날 ③ 더 빠른 날(먼저 나온 날을 지킨다)
			if (isBetter(v, b)) bestIdx = i;
		}
		if (bestIdx >= 0) list.get(bestIdx).setBest(true);
		return list;
	}

	// a 가 b 보다 「가장 좋은 날」로 낫나 (같으면 false → 먼저 나온 날이 남는다)
	private static boolean isBetter(DayScoreVO a, DayScoreVO b) {
		if (a.getCompareScore() != b.getCompareScore()) return a.getCompareScore() > b.getCompareScore();
		if (a.getScore().getScore() != b.getScore().getScore()) return a.getScore().getScore() > b.getScore().getScore();
		if (a.isUncertain() != b.isUncertain()) return !a.isUncertain();
		return false;
	}

	// 공통 근거만으로 계산한 점수: 100 - (공통 근거의 감점 합).
	// 특보 상한은 공통 근거가 아니어도 이미 알려졌으면 적용한다 (나쁜 소식은 자료가 있는 날만 알 수 있어도 무시하면 안 된다)
	static int compareValue(ScoreVO s, Set<String> common) {
		int deduct = 0;
		int cap = 100;
		for (ChipVO c : s.getChips()) {
			if ("warn".equals(c.getKey())) cap = c.getCap();
			if (!common.contains(c.getKey())) continue;
			deduct += -c.getDelta();
		}
		return Math.min(Math.max(0, 100 - deduct), cap);
	}

	private static String labelOf(String key) {
		switch (key) {
		case "weather": return "날씨";
		case "dust": return "미세먼지";
		case "uv": return "자외선";
		case "crowd": return "붐빔";
		default: return "특보";
		}
	}
}
