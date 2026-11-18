package com.inha.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// 한 지역(시도 + 선택한 시군구)의 새 API 자료 묶음: 미세먼지 · 자외선 · 일출몰 · 붐빔 · 기상특보
// 각 자료는 따로 불러서 하나가 실패해도 나머지는 그대로 쓴다 (실패한 것은 값이 null 이고 ...Error 에 이유가 있다).
// 여행 지수(TravelScore)와 화면은 이 묶음에서 날짜(0~10일 뒤)별로 값을 꺼내 쓴다.
public class RegionEvidence {

	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

	private String sido = "";
	private String sigungu = "";
	private String stem = "";                // 시군구 이름 앞부분 ("경주") — 측정소 · 특보 세부지역 맞춤용, 모르면 ""

	private AirVO airNow;                    // 실시간 (오늘)
	private String airNowError = "";
	private List<AirForecastVO> airForecast; // 예보 (오늘 · 내일 · 모레)
	private String airForecastError = "";
	private UvVO uv;
	private String uvError = "";
	private SunVO sun;                       // 고른 날의 해 뜨고 지는 시각
	private String sunError = "";
	private CrowdVO crowd;
	private String crowdError = "";
	private WarningVO warning;               // 전국 특보 현황
	private String warningError = "";
	private final List<WarningItem> current = new ArrayList<>(); // 이 시도의 현재 특보
	private final List<WarningItem> prelim = new ArrayList<>();  // 이 시도의 예비특보

	// 이 지역에서 어느 날이든 값이 있는 근거 이름들 (TravelScore 가 한 번만 계산해 둔다. 불확실 표시의 기준)
	private java.util.Set<String> horizonKeys;
	java.util.Set<String> getHorizonKeys() { return horizonKeys; }
	void setHorizonKeys(java.util.Set<String> keys) { this.horizonKeys = keys; }

	// ---- 화면용 작은 묶음 ----

	// 미세먼지 한 줄: 오늘은 실시간 측정값, 내일 · 모레는 예보 등급
	public static class AirView {
		private int grade;            // 1 좋음 · 2 보통 · 3 나쁨 · 4 매우나쁨
		private String gradeText = "";
		private String source = "";   // "실시간" · "예보"
		private Integer pm10, pm25;   // 예보는 null
		private String pm10Text = "", pm25Text = "";
		private String time = "";     // 측정 시각 또는 예보 발표 시각
		private String cause = "";    // 예보 원인 한 줄 (있을 때만)
		private String scope = "";    // 실시간 값의 범위: "경북 평균 · 측정소 52곳" (예보는 "")
		public boolean isBad() { return grade >= 3; }
		public int getGrade() { return grade; }
		public String getGradeText() { return gradeText; }
		public String getSource() { return source; }
		public Integer getPm10() { return pm10; }
		public Integer getPm25() { return pm25; }
		public String getPm10Text() { return pm10Text; }
		public String getPm25Text() { return pm25Text; }
		public String getTime() { return time; }
		public String getCause() { return cause; }
		public String getScope() { return scope; }
	}

	// 자외선 한 줄
	public static class UvView {
		private int max;
		private int level;
		private String levelText = "";
		private String advice = "";
		public int getMax() { return max; }
		public int getLevel() { return level; }
		public String getLevelText() { return levelText; }
		public String getAdvice() { return advice; }
	}

	// ---- 불러오기 ----

	// sido · sigungu: TourAPI 코드(sigungu 는 "" 가능). addr: 관광지 주소(시군구 이름을 거기서 뽑는다, "" 가능)
	// lat · lon: 해 뜨고 지는 시각을 구할 좌표. sunDate 가 null 이면 일출몰은 안 부른다.
	// 서블릿 스레드에서만 부를 것 (풀 안의 작업이 다른 작업을 기다리면 안 된다)
	public static RegionEvidence load(String key, String sido, String sigungu, String addr, double lat, double lon, LocalDate sunDate) {
		RegionEvidence ev = new RegionEvidence();
		ev.sido = sido == null ? "" : sido;
		ev.sigungu = sigungu == null ? "" : sigungu;
		boolean known = RegionCodes.of(ev.sido) != null;
		boolean hasGu = !ev.sigungu.isEmpty();
		ev.stem = hasGu ? stem(addr) : "";
		String unknown = "이 지역의 코드를 몰라서 자료를 못 불러왔어요.";

		String[] airNames = RegionCodes.airNowNames(ev.sido, ev.sigungu);
		String[] fcNames = RegionCodes.airForecastNames(ev.sido, ev.sigungu);
		String stationKw = ev.stem;

		// 독립 호출을 한꺼번에 시작
		CompletableFuture<AirVO> fNow = !known ? null : ApiClient.async(() -> new AirDAO(key).realtime(airNames, stationKw));
		CompletableFuture<List<AirForecastVO>> fFc = !known ? null : ApiClient.async(() -> new AirDAO(key).forecast(fcNames));
		CompletableFuture<UvVO> fUv = !known ? null : ApiClient.async(() -> new UvDAO(key).forecast(ev.sido, ev.sigungu));
		boolean wantSun = sunDate != null && lat > 30 && lat < 40 && lon > 120 && lon < 135;
		CompletableFuture<SunVO> fSun = !wantSun ? null : ApiClient.async(() -> new SunDAO(key).times(lat, lon, sunDate));
		CompletableFuture<CrowdVO> fCrowd = !known ? null : ApiClient.async(() -> new CrowdDAO(key).forecast(ev.sido, ev.sigungu));
		CompletableFuture<WarningVO> fWarn = ApiClient.async(() -> new WarningDAO(key).status());

		// 각각 따로 기다린다: 실패하면 그 자료만 비워 두고 이유를 남긴다
		if (fNow == null) {
			ev.airNowError = ev.airForecastError = ev.uvError = ev.crowdError = unknown;
		} else {
			try { ev.airNow = ApiClient.await(fNow); } catch (ApiException e) { ev.airNowError = e.getMessage(); }
			try { ev.airForecast = ApiClient.await(fFc); } catch (ApiException e) { ev.airForecastError = e.getMessage(); }
			try { ev.uv = ApiClient.await(fUv); } catch (ApiException e) { ev.uvError = e.getMessage(); }
			try { ev.crowd = ApiClient.await(fCrowd); } catch (ApiException e) { ev.crowdError = e.getMessage(); }
		}
		if (fSun != null) {
			try { ev.sun = ApiClient.await(fSun); } catch (ApiException e) { ev.sunError = e.getMessage(); }
		}
		try {
			ev.warning = ApiClient.await(fWarn);
			String[] names = RegionCodes.warnNames(ev.sido, ev.sigungu); // 광주·전남은 시군구로 광주 / 전남을 가른다
			ev.current.addAll(ev.warning.currentForSido(names));
			ev.prelim.addAll(ev.warning.prelimForSido(names));
		} catch (ApiException e) {
			ev.warningError = e.getMessage();
		}
		return ev;
	}

	// 주소에서 시군구 이름 앞부분을 뽑는다: "경상북도 경주시 …" → "경주", "광주광역시 동구 …" → ""(한 글자라 쓸모없음)
	static String stem(String addr) {
		if (addr == null) return "";
		String[] t = addr.trim().split("\\s+");
		if (t.length < 2) return "";
		String name = t[1];
		if (!name.matches(".+[시군구]")) return ""; // 세종처럼 두 번째 낱말이 시군구가 아니면 포기
		String s = name.substring(0, name.length() - 1);
		return s.length() >= 2 ? s : "";
	}

	// ---- 날짜별로 꺼내 쓰기 (day = 오늘부터 며칠 뒤, 0~10) ----

	// 미세먼지: 오늘은 실시간(값이 있으면), 아니면 예보에서 그 날짜 칸. 없으면 null
	public AirView airView(int day) {
		LocalDate date = Area.dayOf(day);
		if (day == 0 && airNow != null && airNow.isHasData()) {
			AirView v = new AirView();
			v.grade = airNow.getGrade();
			v.gradeText = airNow.getGradeText();
			v.source = "실시간";
			v.pm10 = airNow.getPm10();
			v.pm25 = airNow.getPm25();
			v.pm10Text = airNow.getPm10() == null ? "-" : airNow.getPm10() + " " + airNow.getPm10GradeText();
			v.pm25Text = airNow.getPm25() == null ? "-" : airNow.getPm25() + " " + airNow.getPm25GradeText();
			v.time = airNow.getDataTime();
			v.scope = airNow.getScopeText();
			return v;
		}
		if (airForecast != null) {
			for (AirForecastVO f : airForecast) {
				if (!date.equals(f.getDate()) || f.getGrade() <= 0) continue;
				AirView v = new AirView();
				v.grade = f.getGrade();
				v.gradeText = f.getGradeText();
				v.source = "예보";
				v.pm10Text = f.getPm10GradeText();
				v.pm25Text = f.getPm25GradeText();
				v.time = f.getIssued();
				v.cause = f.getCause() == null ? "" : f.getCause();
				return v;
			}
		}
		return null;
	}

	// 미세먼지 자료가 없을 때 이유
	public String airMissingReason(int day) {
		if (day >= 3) return "미세먼지 예보는 모레까지만 나와요.";
		if (!airNowError.isEmpty() && !airForecastError.isEmpty()) return airForecastError;
		if (day == 0 && !airNowError.isEmpty()) return airNowError;
		if (!airForecastError.isEmpty()) return airForecastError;
		return "이날 미세먼지 자료가 아직 없어요.";
	}

	// 자외선: 그 날 가장 센 값. 없으면 null
	public UvView uvView(int day) {
		if (uv == null) return null;
		Integer max = uv.maxDaytimeOn(Area.dayOf(day)); // 낮 칸이 없는 날(예보가 새벽까지만)은 자료 없음
		if (max == null) return null;
		UvView v = new UvView();
		v.max = max;
		v.level = UvVO.level(max);
		v.levelText = UvVO.levelText(max);
		v.advice = UvVO.advice(max);
		return v;
	}

	public String uvMissingReason(int day) {
		if (!uvError.isEmpty()) return uvError;
		return day >= 3 ? "자외선 예보는 글피까지만 나와요." : "이날 자외선 자료가 아직 없어요.";
	}

	// 붐빔: 시군구 전체 평균 집중률(0~100). 없으면 null
	public Double crowdAverage(int day) {
		if (crowd == null || !crowd.isAvailable()) return null;
		return crowd.averageOn(Area.dayOf(day).format(YMD));
	}

	// 관광지 이름과 맞는 붐빔 값. 이름이 안 맞거나 그 날 값이 없으면 null
	public Double crowdOfSpot(String title, int day) {
		if (crowd == null || !crowd.isAvailable() || title == null) return null;
		return crowd.rateOn(title, Area.dayOf(day).format(YMD));
	}

	// 붐빔 자료가 없는 까닭이 「시군구를 안 골라서」인가 (자료 자체가 없는 지역이면 false)
	public boolean crowdNeedsSigungu() {
		return RegionCodes.crowdSupported(sido) && !sido.equals("36110") && sigungu.isEmpty();
	}

	public String crowdMissingReason() {
		if (!crowdError.isEmpty()) return crowdError;
		if (crowd != null && !crowd.isAvailable()) return crowd.getMessage();
		return "이날 붐빔 예측이 없어요.";
	}

	// 이 특보가 고른 시군구에 해당하나 (시군구를 안 골랐으면 시도 전체라 늘 해당)
	// 울릉도 · 서해5도는 시도 안에서도 따로 떨어진 곳이라, 시군구를 골랐으면 그 섬 시군구(울릉 · 옹진)일 때만 해당한다
	// 세부 지역 글을 읽는 규칙("…제외" · 권역 이름)은 WarningItem.appliesToStem 에 있다
	private boolean applies(WarningItem w) {
		if (stem.isEmpty()) return true;
		String name = w.getSido();
		if (name.startsWith("울릉도")) return stem.equals("울릉");
		if (name.equals("서해5도")) return stem.equals("옹진");
		return w.appliesToStem(stem);
	}

	// 고른 시군구에 해당하는 특보 (시군구를 안 골랐으면 시도 전체)와 같은 시도의 다른 지역 특보 수 — 배너에서 가른다
	public List<WarningItem> hereCurrent() { return filter(current, true); }
	public List<WarningItem> herePrelim() { return filter(prelim, true); }
	// 그 날(day) 발효가 예정된 예비특보만 (오늘 · 내일 · 모레만 있다. 그 뒤는 아직 발표 전이라 빈 목록)
	public List<WarningItem> herePrelimOn(int day) {
		List<WarningItem> list = new ArrayList<>();
		if (day > 2) return list;
		for (WarningItem w : herePrelim()) if (w.appliesOn(Area.dayOf(day))) list.add(w);
		return list;
	}
	public int elsewhereCount() { return filter(current, false).size() + filter(prelim, false).size(); }

	private List<WarningItem> filter(List<WarningItem> src, boolean wantApplies) {
		List<WarningItem> list = new ArrayList<>();
		for (WarningItem w : src) if (applies(w) == wantApplies) list.add(w);
		return list;
	}

	// 그 날 점수에 영향을 주는 특보: 현재 특보는 오늘 · 내일, 예비특보는 모레까지 (그 뒤는 아직 발표 전).
	// 예비특보는 발효 예정 날짜가 그 날과 같은 것만 (오늘 오후에 끝나는 예비특보가 모레 점수까지 묶지 않게). 날짜를 못 읽은 건 모레까지 다 쓴다
	public List<WarningItem> warningsFor(int day) {
		List<WarningItem> list = new ArrayList<>();
		if (day <= 1) for (WarningItem w : current) if (applies(w)) list.add(w);
		if (day <= 2) for (WarningItem w : prelim) if (applies(w) && w.appliesOn(Area.dayOf(day))) list.add(w);
		return list;
	}

	// ---- getter ----
	public String getSido() { return sido; }
	public String getSigungu() { return sigungu; }
	public String getStem() { return stem; }
	public AirVO getAirNow() { return airNow; }
	public String getAirNowError() { return airNowError; }
	public List<AirForecastVO> getAirForecast() { return airForecast; }
	public String getAirForecastError() { return airForecastError; }
	public UvVO getUv() { return uv; }
	public String getUvError() { return uvError; }
	public SunVO getSun() { return sun; }
	public String getSunError() { return sunError; }
	public CrowdVO getCrowd() { return crowd; }
	public String getCrowdError() { return crowdError; }
	public WarningVO getWarning() { return warning; }
	public String getWarningError() { return warningError; }
	public List<WarningItem> getCurrent() { return current; }
	public List<WarningItem> getPrelim() { return prelim; }
}
