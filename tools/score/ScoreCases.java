import com.inha.model.AirVO;
import com.inha.model.ChipVO;
import com.inha.model.CoursePlanner;
import com.inha.model.HourVO;
import com.inha.model.RecommendVO;
import com.inha.model.Recommender;
import com.inha.model.RegionEvidence;
import com.inha.model.ScoreVO;
import com.inha.model.TravelScore;
import com.inha.model.WarningItem;
import com.inha.model.WeatherVO;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// 여행 지수 밸런스 시험대: 가짜 날씨 · 근거로 점수표를 찍고 목표 범위와 대 본다 (API 안 부름)
// 실행: sh tools/score/run.sh   (종료 코드 = 실패 줄 수가 0 이면 0)
public class ScoreCases {

	// 짝 규칙: 같은 조건에서 실외가 실내보다 높은 건 이상적인 날뿐
	static final int IDEAL = 0; // 제약 없음
	static final int GE = 1;    // 실내 >= 실외
	static final int GT = 2;    // 실내 > 실외

	// ---- 가짜 근거 묶음 (공공 API 안 부름) ----
	static class FakeEv extends RegionEvidence {
		int dustGrade = 1;      // 0 이면 자료 없음
		boolean dustHoleToday;  // 오늘만 미세먼지 빠짐 (다른 날엔 있음 → 불확실)
		int uvMax = 4;          // 0 이면 자료 없음
		Double crowd = 30.0;    // null 이면 자료 없음
		WarningItem warn;       // null 이면 특보 없음

		@Override public AirView airView(int day) {
			if (dustGrade <= 0 || (dustHoleToday && day == 0)) return null;
			AirView v = new AirView();
			set(v, "grade", dustGrade);
			set(v, "gradeText", AirVO.gradeText(dustGrade));
			set(v, "source", "예보");
			set(v, "pm10Text", AirVO.gradeText(dustGrade));
			set(v, "pm25Text", AirVO.gradeText(dustGrade));
			return v;
		}

		@Override public UvView uvView(int day) {
			if (uvMax <= 0) return null;
			UvView v = new UvView();
			set(v, "max", uvMax);
			set(v, "level", com.inha.model.UvVO.level(uvMax));
			set(v, "levelText", com.inha.model.UvVO.levelText(uvMax));
			set(v, "advice", "");
			return v;
		}

		@Override public Double crowdAverage(int day) { return crowd; }

		@Override public List<WarningItem> warningsFor(int day) {
			List<WarningItem> list = new ArrayList<>();
			if (warn != null && day <= 1) list.add(warn);
			return list;
		}
	}

	static void set(Object o, String name, Object value) {
		try {
			Field f = o.getClass().getDeclaredField(name);
			f.setAccessible(true);
			f.set(o, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(name, e);
		}
	}

	static WarningItem warn(String kind) {
		WarningItem w = new WarningItem();
		w.setKind(kind);
		w.setSido("가짜도");
		return w;
	}

	// ---- 케이스 ----
	static class Case {
		String name;
		String sky = "맑음";
		int min, max, pop;
		boolean rainy;
		String rainType = "";
		boolean noEv;          // ev = null
		int dust = 1, uv = 4;  // 미세먼지 등급 1~4 · 자외선 지수
		double crowd = 30;
		WarningItem warn;
		boolean dustHole;
		int outLo, outHi, inLo, inHi;
		int pair = GT;
		boolean outGiven = true; // 실외 목표가 요청서에 있는 값인가 (false = 내가 정한 가정)

		Case(String name) { this.name = name; }
		Case sky(String s) { sky = s; return this; }
		Case t(int min, int max) { this.min = min; this.max = max; return this; }
		Case pop(int p) { pop = p; return this; }
		Case rain(String type) { rainy = true; rainType = type; return this; }
		Case dust(int g) { dust = g; return this; }
		Case uv(int u) { uv = u; return this; }
		Case crowd(double c) { crowd = c; return this; }
		Case warn(String kind) { warn = ScoreCases.warn(kind); return this; }
		Case dustHole() { dustHole = true; return this; }
		Case noEv() { noEv = true; return this; }
		Case out(int lo, int hi) { outLo = lo; outHi = hi; return this; }
		Case in(int lo, int hi) { inLo = lo; inHi = hi; return this; }
		Case pair(int p) { pair = p; return this; }
		Case assumed() { outGiven = false; return this; }

		WeatherVO weather() {
			WeatherVO w = new WeatherVO();
			w.setDateLabel("가짜");
			w.setMinTemp(min);
			w.setMaxTemp(max);
			w.setMaxPop(pop);
			w.setSky(sky);
			w.setRainy(rainy);
			w.setRainType(rainType);
			w.setRainTime("");
			return w;
		}

		RegionEvidence evidence() {
			if (noEv) return null;
			FakeEv ev = new FakeEv();
			ev.dustGrade = dust;
			ev.dustHoleToday = dustHole;
			ev.uvMax = uv;
			ev.crowd = crowd;
			ev.warn = warn;
			return ev;
		}
	}

	static List<Case> cases() {
		List<Case> c = new ArrayList<>();
		// 이상적인 날 (맑고 18~25° · 미세먼지 좋음)
		c.add(new Case("맑음 최고22 최저13").t(13, 22).out(90, 100).in(85, 100).pair(IDEAL));
		c.add(new Case("맑음 최고25 최저17").t(17, 25).out(90, 100).in(85, 100).pair(IDEAL));
		c.add(new Case("맑음 최고18 최저9").t(9, 18).out(90, 100).in(85, 100).pair(IDEAL));
		// 추위
		c.add(new Case("맑음 최고10 최저2 (늦가을)").t(2, 10).out(70, 84).in(80, 95));
		c.add(new Case("맑음 최고5 최저-1").t(-1, 5).out(55, 72).in(80, 95));
		c.add(new Case("맑음 최고3 최저-4").t(-4, 3).out(55, 72).in(80, 95));
		c.add(new Case("맑음 최고0 최저-6").t(-6, 0).out(35, 55).in(80, 95));
		c.add(new Case("맑음 최고-5 최저-12").t(-12, -5).out(20, 45).in(80, 95));
		// 하늘
		c.add(new Case("흐림 최고8 최저3 (쌀쌀)").sky("흐림").t(3, 8).out(60, 78).in(80, 95));
		c.add(new Case("구름많음 최고15 최저8").sky("구름많음").t(8, 15).out(80, 96).in(85, 100).pair(GE).assumed());
		c.add(new Case("흐림 최고20 최저12").sky("흐림").t(12, 20).out(80, 96).in(85, 100).pair(GE).assumed());
		// 강수
		c.add(new Case("맑음 최고18 강수확률20%").t(10, 18).pop(20).out(85, 100).in(85, 100).pair(GE).assumed());
		c.add(new Case("맑음 최고18 강수확률40%").t(10, 18).pop(40).out(65, 82).in(85, 100));
		c.add(new Case("강수확률70% 최고18 (비 판정)").sky("흐림").t(10, 18).pop(70).rain("").out(15, 45).in(80, 95));
		c.add(new Case("비 최고16 최저10").sky("흐림").t(10, 16).pop(80).rain("비").out(15, 45).in(80, 95));
		c.add(new Case("눈 최고1 최저-4").sky("흐림").t(-4, 1).pop(70).rain("눈").out(15, 45).in(80, 95));
		// 더위
		c.add(new Case("맑음 최고30 최저21").t(21, 30).out(75, 92).in(85, 100).assumed());
		c.add(new Case("맑음 최고33 최저24").t(24, 33).out(40, 65).in(80, 95));
		c.add(new Case("맑음 최고36 최저27").t(27, 36).out(40, 65).in(80, 95));
		// 미세먼지 · 자외선 · 붐빔 (날씨는 이상적)
		c.add(new Case("이상적 날씨 + 미세먼지 나쁨").t(13, 22).dust(3).out(60, 80).in(80, 95));
		c.add(new Case("이상적 날씨 + 미세먼지 매우나쁨").t(13, 22).dust(4).out(35, 60).in(80, 95).assumed());
		c.add(new Case("이상적 날씨 + 자외선 위험(12)").t(13, 22).uv(12).out(75, 89).in(85, 100).assumed());
		c.add(new Case("이상적 날씨 + 붐빔 85%").t(13, 22).crowd(85).out(70, 88).in(75, 90).pair(GE).assumed());
		// 특보
		c.add(new Case("이상적 날씨 + 주의보").t(13, 22).warn("강풍주의보").out(50, 70).in(70, 85).assumed());
		c.add(new Case("이상적 날씨 + 경보").t(13, 22).warn("강풍경보").out(30, 45).in(45, 60).assumed());
		c.add(new Case("이상적 날씨 + 큰 경보(호우)").t(13, 22).warn("호우경보").out(15, 30).in(30, 45).assumed());
		// 자료 빠짐
		c.add(new Case("자료 빠짐: 오늘만 미세먼지 없음").t(13, 22).dustHole().out(75, 90).in(75, 90).pair(GE).assumed());
		c.add(new Case("자료 없음: 날씨만 (ev=null) 이상적").t(13, 22).noEv().out(70, 90).in(70, 90).pair(GE).assumed());
		c.add(new Case("자료 없음: 날씨만 (ev=null) 비").sky("흐림").t(10, 16).pop(80).rain("비").noEv().out(15, 45).in(80, 95));
		// 나쁜 것 여러 개
		c.add(new Case("겹침A 비+먼지나쁨+붐빔85+UV위험+주의보").sky("흐림").t(10, 16).pop(80).rain("비").dust(3).crowd(85).uv(12)
				.warn("강풍주의보").out(0, 30).in(55, 85).assumed());
		c.add(new Case("겹침B 한파 최고-2+먼지나쁨+붐빔70").t(-9, -2).dust(3).crowd(70).out(10, 40).in(60, 90).assumed());
		c.add(new Case("겹침C 폭염 최고35+먼지나쁨+UV위험").t(26, 35).dust(3).uv(12).out(15, 45).in(75, 92).assumed());
		return c;
	}

	static String chipDeltas(ScoreVO s) {
		StringBuilder b = new StringBuilder();
		for (ChipVO c : s.getChips()) {
			if ("warn".equals(c.getKey())) {
				b.append(c.isMissing() ? " 특보:-" : " 특보상한:" + c.getCap());
			} else {
				b.append(" ").append(c.getLabel()).append(c.isMissing() ? ":-" : ":" + c.getDelta());
			}
		}
		return b.toString().trim();
	}

	// 최고 · 최저로 시간별 기온 곡선(15시 최고)을 만든 하루 날씨
	static WeatherVO dayWeather(String sky, int min, int max, int pop) {
		WeatherVO w = new WeatherVO();
		w.setDateLabel("가짜");
		w.setMinTemp(min);
		w.setMaxTemp(max);
		w.setMaxPop(pop);
		w.setSky(sky);
		w.setRainType("");
		w.setRainTime("");
		List<HourVO> hours = new ArrayList<>();
		for (int h = 6; h <= 21; h++) {
			HourVO v = new HourVO();
			v.setTime(String.format("%02d시", h));
			v.setTemp(String.valueOf(min + (int) Math.round((max - min) * Math.max(0, 1 - Math.abs(h - 15) / 9.0))));
			v.setPop(pop);
			v.setSky(sky);
			hours.add(v);
		}
		w.setHours(hours);
		return w;
	}

	static FakeEv fakeEv(int dust, int uv, double crowd) {
		FakeEv ev = new FakeEv();
		ev.dustGrade = dust;
		ev.uvMax = uv;
		ev.crowd = crowd;
		return ev;
	}

	// 시험대에 있는 케이스 말고 날씨 조합을 쓸어서 보는 검사들 (API 안 부름)
	static void sweepChecks(List<String> failing) {
		String[] skies = { "맑음", "구름많음", "흐림" };
		int[] drops = { 3, 8, 12 };
		int[] pops = { 0, 10, 20, 30, 40 };
		int[] dusts = { 1, 2 }; // 좋음 · 보통
		int combos = 0, outdoorDays = 0, scoreBad = 0, timeBad = 0;
		List<String> examples = new ArrayList<>();
		com.inha.model.TourVO outdoorSpot = new com.inha.model.TourVO();
		for (String sky : skies) {
			for (int max = -10; max <= 38; max++) {
				for (int drop : drops) {
					for (int pop : pops) {
						WeatherVO w = dayWeather(sky, max - drop, max, pop);
						for (int dust : dusts) {
							combos++;
							RecommendVO rec = Recommender.recommend(w, dust);
							if (!RecommendVO.OUTDOOR.equals(rec.getMode())) continue;
							outdoorDays++;
							// 가장 나쁜 보통 수준: 먼지 보통 · 자외선 높음(6) · 붐빔 45%
							int score = TravelScore.score(fakeEv(dust, 6, 45), 1, w, false, null).getScore();
							if (score < 70) {
								scoreBad++;
								if (examples.size() < 5) examples.add(sky + " 최고" + max + " 최저" + (max - drop) + " 강수" + pop + "% 먼지" + dust + " → 실외 " + score);
							}
							if (dust == 1) {
								// 같은 화면에 나오는 시간 칸과 안 어긋나나: 실외 위주 날은 야외 좋은 칸이 4개 이상
								int good = 0;
								for (int s : new int[] { 10, 12, 14, 16, 18 }) {
									if (CoursePlanner.blockProblem(w, s).isEmpty()) good++;
								}
								String best = CoursePlanner.bestTime(outdoorSpot, w, 1, java.time.LocalTime.of(8, 0));
								if (good < 4 || best.contains("좋은 시간대가 없어요")) {
									timeBad++;
									if (examples.size() < 5) examples.add(sky + " 최고" + max + " 최저" + (max - drop) + " 강수" + pop + "% → 실외 위주인데 좋은 칸 " + good + "/5");
								}
							}
						}
					}
				}
			}
		}
		System.out.println();
		System.out.println("쓸어보기 " + combos + "조합 · 실외 위주 " + outdoorDays + "조합 · 점수 70 미만 " + scoreBad + " · 시간 칸 어긋남 " + timeBad);
		if (scoreBad > 0) failing.add("쓸어보기: 실외 위주 추천인데 실외 점수 70 미만 " + scoreBad + "조합 (예 " + examples + ")");
		if (timeBad > 0) failing.add("쓸어보기: 실외 위주 추천인데 시간 칸이 대부분 실내 권장 " + timeBad + "조합 (예 " + examples + ")");

		// 계단 검사: 맑은 날 최고 1° 차이로 실외 점수가 8점 넘게 바뀌면 안 된다
		int prev = -1, worst = 0;
		String worstAt = "";
		for (int max = -10; max <= 40; max++) {
			int sc = TravelScore.score(fakeEv(1, 4, 30), 1, dayWeather("맑음", max - 8, max, 0), false, null).getScore();
			if (prev >= 0 && Math.abs(sc - prev) > worst) { worst = Math.abs(sc - prev); worstAt = (max - 1) + "→" + max + "°"; }
			prev = sc;
		}
		System.out.println("맑은 날 최고 1° 당 가장 큰 점수 변화 " + worst + "점 (" + worstAt + ")");
		if (worst > 8) failing.add("계단: 최고 " + worstAt + " 에서 점수가 " + worst + "점 바뀜 (8점 이하여야 함)");

		// 참고표: 맑은 날(최저 = 최고 - 8, 먼지 좋음 · 자외선 4 · 붐빔 30%) 최고기온별 실외 · 실내 점수와 추천 방향
		System.out.println();
		System.out.println("| 최고 | 최저 | 실외 | 실내 | 추천 방향 |");
		System.out.println("|---|---|---|---|---|");
		for (int max : new int[] { -5, 0, 3, 5, 6, 8, 10, 11, 12, 13, 14, 15, 16, 17, 22, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36 }) {
			WeatherVO w = dayWeather("맑음", max - 8, max, 0);
			FakeEv ev = fakeEv(1, 4, 30);
			System.out.println("| " + max + " | " + (max - 8) + " | " + TravelScore.score(ev, 1, w, false, null).getScore() + " | "
					+ TravelScore.score(ev, 1, w, true, null).getScore() + " | " + Recommender.recommend(w, 1).getMode() + " |");
		}

		// 쌀쌀한 날(최고 14° 이하) 맑은 날 실외 관광지는 90점을 넘지 않는다
		for (int max = -10; max <= 14; max++) {
			for (int drop : new int[] { 3, 8 }) {
				int sc = TravelScore.score(fakeEv(1, 4, 30), 1, dayWeather("맑음", max - drop, max, 0), false, null).getScore();
				if (sc >= 90) failing.add("쌀쌀한 날 90점 이상: 맑음 최고" + max + " 최저" + (max - drop) + " → 실외 " + sc);
			}
		}

		// 자료가 모자라 90점으로 막힌 날은 노트에 그렇게 적는다
		ScoreVO sparse = TravelScore.score(null, 0, dayWeather("맑음", 12, 22, 0), false, null);
		boolean sparseOk = sparse.getScore() == 90 && sparse.getNote().contains("90점까지만");
		System.out.println("자료 모자람 노트: " + sparse.getScore() + "점 · " + sparse.getNote() + " → " + (sparseOk ? "통과" : "실패"));
		if (!sparseOk) failing.add("자료 모자람 노트에 90점 상한 설명이 없음: " + sparse.getNote());

		// 오늘 늦게 열어 최고기온을 남은 시간에서 뽑은 날은 날씨 칩에 그렇게 알린다
		WeatherVO late = dayWeather("맑음", 6, 8, 0);
		late.setMaxFromHours(true);
		ScoreVO lateScore = TravelScore.score(fakeEv(1, 4, 30), 0, late, false, null);
		boolean lateOk = lateScore.getChips().get(0).getHint().contains("남은 시간");
		System.out.println("남은 시간 기온 안내: " + (lateOk ? "통과" : "실패"));
		if (!lateOk) failing.add("남은 시간 기온 기준이라는 안내가 날씨 칩에 없음");
	}

	public static void main(String[] args) {
		List<Case> cases = cases();
		List<String> failing = new ArrayList<>();
		int rows = 0;
		System.out.println("| # | 케이스 | 구분 | 점수 | 등급 | 추천 방향 | 칩별 감점 | 목표 | 목표 출처 | 결과 |");
		System.out.println("|---|---|---|---|---|---|---|---|---|---|");
		int n = 0;
		for (Case c : cases) {
			n++;
			WeatherVO w = c.weather();
			RegionEvidence ev = c.evidence();
			int airGrade = c.noEv ? 0 : c.dust;
			RecommendVO rec = Recommender.recommend(w, airGrade);
			ScoreVO out = TravelScore.score(ev, 0, w, false, null);
			ScoreVO in = TravelScore.score(ev, 0, w, true, null);

			boolean outOk = out.getScore() >= c.outLo && out.getScore() <= c.outHi;
			boolean inOk = in.getScore() >= c.inLo && in.getScore() <= c.inHi;
			boolean pairOk = c.pair == IDEAL || (c.pair == GE ? in.getScore() >= out.getScore() : in.getScore() > out.getScore());

			rows += 2;
			String src = c.outGiven ? "지정" : "가정";
			System.out.println("| " + n + " | " + c.name + " | 실외 | " + out.getScore() + " | " + out.getGrade() + " | " + rec.getMode()
					+ " | " + chipDeltas(out) + " | " + c.outLo + "~" + c.outHi + " | " + src + " | " + (outOk ? "통과" : "실패") + " |");
			System.out.println("| " + n + " | " + c.name + " | 실내 | " + in.getScore() + " | " + in.getGrade() + " | " + rec.getMode()
					+ " | " + chipDeltas(in) + " | " + c.inLo + "~" + c.inHi + " | " + (c.pair == IDEAL ? "지정" : "지정/가정") + " | "
					+ (inOk ? "통과" : "실패") + (pairOk ? "" : " · 짝 규칙 실패") + " |");
			if (!outOk) failing.add(n + " " + c.name + " 실외 " + out.getScore() + " (목표 " + c.outLo + "~" + c.outHi + ")");
			if (!inOk) failing.add(n + " " + c.name + " 실내 " + in.getScore() + " (목표 " + c.inLo + "~" + c.inHi + ")");
			if (!pairOk) failing.add(n + " " + c.name + " 짝 규칙: 실내 " + in.getScore() + " vs 실외 " + out.getScore()
					+ (c.pair == GT ? " (실내가 더 높아야 함)" : " (실내가 낮으면 안 됨)"));
		}
		// 말과 점수가 어긋나지 않나: 「실외 위주 추천」(OUTDOOR) 인데 실외 점수가 좋음(70) 미만이면 안 된다 (특보는 추천에 안 들어가서 뺀다)
		for (int i = 0; i < cases.size(); i++) {
			Case c = cases.get(i);
			if (c.warn != null) continue;
			WeatherVO w = c.weather();
			RecommendVO rec = Recommender.recommend(w, c.noEv ? 0 : c.dust);
			int os = TravelScore.score(c.evidence(), 0, w, false, null).getScore();
			if (RecommendVO.OUTDOOR.equals(rec.getMode()) && os < 70) {
				failing.add((i + 1) + " " + c.name + " 말과 점수 어긋남: 실외 위주 추천인데 실외 점수 " + os);
			}
		}

		// 야외에 좋은 시간(CoursePlanner) 판정: 시간별 기온은 0° 이하 추움 · 32° 초과 더움 · 강수 40% (하루 최고 5° 이하는 중기 칸만)
		System.out.println();
		System.out.println("| 시간 판정 | 기온 | 강수확률 | 하늘 | 판정 | 기대 | 결과 |");
		System.out.println("|---|---|---|---|---|---|---|");
		Object[][] hourCases = {
				{ "5도 괜찮음", 5, 0, "맑음", true },
				{ "6도 괜찮음", 6, 0, "맑음", true },
				{ "1도 괜찮음", 1, 0, "맑음", true },
				{ "0도 추움", 0, 0, "맑음", false },
				{ "-3도 추움", -3, 0, "맑음", false },
				{ "3도 흐림 괜찮음", 3, 10, "흐림", true },
				{ "12도 괜찮음", 12, 10, "맑음", true },
				{ "22도 괜찮음", 22, 0, "맑음", true },
				{ "32도 괜찮음", 32, 0, "맑음", true },
				{ "33도 더움", 33, 0, "맑음", false },
				{ "강수확률 40% 나쁨", 18, 40, "맑음", false },
				{ "강수확률 30% 괜찮음", 18, 30, "맑음", true },
				{ "비 하늘 나쁨", 16, 80, "비", false },
		};
		int hourFail = 0;
		for (Object[] hc : hourCases) {
			HourVO h = new HourVO();
			h.setTime("12시");
			h.setTemp(String.valueOf(hc[1]));
			h.setPop((Integer) hc[2]);
			h.setSky((String) hc[3]);
			String problem = CoursePlanner.problemOf(h);
			boolean good = problem.isEmpty();
			boolean ok = good == (Boolean) hc[4];
			if (!ok) { hourFail++; failing.add("시간 판정 " + hc[0] + ": " + (good ? "야외 좋음" : problem) + " (기대 " + ((Boolean) hc[4] ? "좋음" : "나쁨") + ")"); }
			System.out.println("| " + hc[0] + " | " + hc[1] + " | " + hc[2] + " | " + hc[3] + " | " + (good ? "야외 좋음" : problem)
					+ " | " + ((Boolean) hc[4] ? "좋음" : "나쁨") + " | " + (ok ? "통과" : "실패") + " |");
		}
		// 중기예보 칸도 같은 기준 (최고기온으로 판정)
		String[][] midCases = { { "맑음", "10", "5", "나쁨" }, { "맑음", "10", "6", "좋음" }, { "맑음", "10", "33", "나쁨" } };
		for (String[] m : midCases) {
			String problem = CoursePlanner.midProblemOf(m[0], Integer.parseInt(m[1]), Integer.valueOf(m[2]));
			boolean ok = problem.isEmpty() == "좋음".equals(m[3]);
			if (!ok) { hourFail++; failing.add("중기 판정 최고 " + m[2] + "°: " + problem); }
			System.out.println("| 중기 칸 | 최고 " + m[2] + " | " + m[1] + " | " + m[0] + " | " + (problem.isEmpty() ? "야외 좋음" : problem)
					+ " | " + m[3] + " | " + (ok ? "통과" : "실패") + " |");
		}

		sweepChecks(failing);

		System.out.println();
		System.out.println("케이스 " + cases.size() + "개 · 줄 " + rows + "개 · 실패 " + failing.size() + "건");
		for (String f : failing) System.out.println("  실패: " + f);
		if (!failing.isEmpty()) System.exit(1);
	}
}
