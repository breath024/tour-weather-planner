package com.inha.model;

// 관광지 한 곳 (한국관광공사 TourAPI 결과)
public class TourVO {
	private String contentId;
	private String contentTypeId;
	private String typeName;   // 관광지 · 문화시설 ...
	private String title;
	private String addr;
	private String image;      // 대표 이미지 URL
	private double lat;        // 위도 (API 의 mapy)
	private double lon;        // 경도 (API 의 mapx)
	private String tel;
	private String homepage;
	private String overview;   // 개요 (상세에서만)
	private boolean indoor;    // 실내 관광지인가
	private String sidoCode;     // 법정동 시도 코드 (lDongRegnCd)
	private String sigunguCode;   // 법정동 시군구 코드 (lDongSignguCd)
	private int distM;         // 기준점에서 거리(m) — 위치기반 조회에서만 채워짐, 아니면 0

	// ---- 이 관광지 자기 자리(격자)의 날씨와 추천 (GridWeather · PlanController 가 채움) ----
	private WeatherVO weather;      // 이 곳 격자의 하루 날씨 (못 구하면 null)
	private boolean weatherApprox;  // 격자를 못 부르거나 한도(16개)를 넘어 가까운 격자 값을 빌려 왔나
	private String recMode;         // 이 곳 날씨로 정한 추천 방향 INDOOR · OUTDOOR · MIXED
	private boolean fit;            // 이 곳 날씨에 이 관광지가 맞나 (추천 목록에 들어가는가)
	private String tip;             // 이 곳 날씨 기준 한 줄 조언

	// ---- 2판: 여행 지수와 붐빔 (PlanController · DetailController 가 채움) ----
	private ScoreVO score;          // 이 곳 · 그 날의 여행 지수 (못 만들면 null)
	private Double crowdRate;       // 이 관광지 이름으로 맞춘 붐빔 집중률 0~100 (맞는 이름이 없으면 null → 배지 없음)

	// ---- 3판: 분류(대분류) · 축제 행사 (TourDAO 가 채움) ----
	private String lclsCode = "";     // 대분류 코드 lclsSystm1 (NA 자연관광 · HS 역사관광 …, 없으면 "")
	private String lcls3Code = "";    // 소분류 코드 lclsSystm3 (EV020200 연극 …, 행사 실내 · 야외 가르기에 쓴다)
	private String lclsName = "";     // 대분류 이름 (lclsSystmCode2 이름표, 못 구하면 "")
	private String eventStart = "";   // 행사 시작일 yyyyMMdd (축제 · 행사만)
	private String eventEnd = "";     // 행사 종료일 yyyyMMdd
	private boolean placeUnknown;    // 행사인데 실내인지 야외인지 모름 (분류가 전통공연 · 기타행사 같은 경우). 이때 indoor 는 false 지만 「야외」라고 단정하지 않는다
	private int eventRefYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).getYear();   // 기간에서 이 해가 아닌 날짜에는 연도를 붙인다
	private String eventWarn = "";    // 야외 행사인데 그 자리 날씨가 비일 때 안내 (아니면 "")

	public String getLclsCode() { return lclsCode; }
	public void setLclsCode(String lclsCode) { this.lclsCode = lclsCode == null ? "" : lclsCode; }
	public String getLcls3Code() { return lcls3Code; }
	public void setLcls3Code(String c) { this.lcls3Code = c == null ? "" : c; }
	public String getLclsName() { return lclsName; }
	public void setLclsName(String lclsName) { this.lclsName = lclsName == null ? "" : lclsName; }
	public String getEventStart() { return eventStart; }
	public void setEventStart(String eventStart) { this.eventStart = eventStart == null ? "" : eventStart; }
	public String getEventEnd() { return eventEnd; }
	public void setEventEnd(String eventEnd) { this.eventEnd = eventEnd == null ? "" : eventEnd; }
	public boolean isPlaceUnknown() { return placeUnknown; }
	public void setPlaceUnknown(boolean placeUnknown) { this.placeUnknown = placeUnknown; }
	public void setEventRefYear(int eventRefYear) { this.eventRefYear = eventRefYear; }
	public String getEventWarn() { return eventWarn; }
	public void setEventWarn(String eventWarn) { this.eventWarn = eventWarn == null ? "" : eventWarn; }
	public boolean isEvent() { return "15".equals(contentTypeId); }

	// 행사 기간 글자: "11/19 ~ 11/21" (하루짜리는 "11/19"), 날짜를 못 읽으면 "".
	// 보는 날의 해(eventRefYear)와 다른 해의 날짜에는 연도를 붙인다: "2022.11.1 ~ 12/31", "11/7 ~ 2027.2.28"
	public String getEventPeriod() {
		return periodLabel(eventStart, eventEnd, eventRefYear);
	}

	// 고른 날이 행사 기간 밖이면 안내 문구, 안이거나 기간을 모르면 "" (start · end: yyyyMMdd)
	public static String eventNotice(String start, String end, java.time.LocalDate day) {
		if (start == null || !start.matches("\\d{8}")) return "";
		String d = day.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
		boolean before = d.compareTo(start) < 0;
		boolean after = end != null && end.matches("\\d{8}") && d.compareTo(end) > 0;
		if (!before && !after) return "";
		return "이날은 행사 기간(" + periodLabel(start, end, day.getYear()) + ")이 아니에요. 열리는 날로 바꿔 보세요.";
	}

	// start · end: yyyyMMdd. CourseItem 도 같이 쓴다
	public static String periodLabel(String start, String end, int refYear) {
		String a = md(start, refYear), b = md(end, refYear);
		if (a.isEmpty()) return "";
		return b.isEmpty() || (a.equals(b) && start.equals(end)) ? a : a + " ~ " + b;
	}

	private static String md(String ymd, int refYear) {
		if (ymd == null || !ymd.matches("\\d{8}")) return "";
		int y = Integer.parseInt(ymd.substring(0, 4));
		int m = Integer.parseInt(ymd.substring(4, 6)), d = Integer.parseInt(ymd.substring(6, 8));
		return y == refYear ? m + "/" + d : y + "." + m + "." + d;   // 이 해가 아니면 「2022.11.1」
	}

	public boolean isHasLocation() {
		return lat > 30 && lat < 40 && lon > 120 && lon < 135;
	}

	// 거리를 km 로 (소수 첫째 자리)
	public double getDistKm() {
		return Math.round(distM / 100.0) / 10.0;
	}

	public WeatherVO getWeather() { return weather; }
	public void setWeather(WeatherVO weather) { this.weather = weather; }
	public boolean isWeatherApprox() { return weatherApprox; }
	// 카드 날씨 비교용 (날씨 없으면 빈칸)
	public String getWxKey() {
		if (weather == null) return "";
		return weather.getIcon() + "|" + weather.getMinTemp() + "|" + weather.getMaxTemp() + "|" + weather.getMaxPop() + "|" + weatherApprox;
	}
	public void setWeatherApprox(boolean weatherApprox) { this.weatherApprox = weatherApprox; }
	public String getRecMode() { return recMode; }
	public void setRecMode(String recMode) { this.recMode = recMode; }
	public boolean isFit() { return fit; }
	public void setFit(boolean fit) { this.fit = fit; }
	public ScoreVO getScore() { return score; }
	public void setScore(ScoreVO score) { this.score = score; }
	public Double getCrowdRate() { return crowdRate; }
	public void setCrowdRate(Double crowdRate) { this.crowdRate = crowdRate; }
	// 붐빔 배지 글자: "붐빔 62% (보통)" (맞는 값이 없으면 "")
	public String getCrowdText() {
		return crowdRate == null ? "" : "붐빔 " + Math.round(crowdRate) + "% (" + CrowdVO.levelText(crowdRate) + ")";
	}
	public String getTip() { return tip; }
	public void setTip(String tip) { this.tip = tip; }

	public String getContentId() { return contentId; }
	public void setContentId(String contentId) { this.contentId = contentId; }
	public String getContentTypeId() { return contentTypeId; }
	public void setContentTypeId(String contentTypeId) { this.contentTypeId = contentTypeId; }
	public String getTypeName() { return typeName; }
	public void setTypeName(String typeName) { this.typeName = typeName; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getAddr() { return addr; }
	public void setAddr(String addr) { this.addr = addr; }
	public String getImage() { return image; }
	public void setImage(String image) { this.image = image; }
	public double getLat() { return lat; }
	public void setLat(double lat) { this.lat = lat; }
	public double getLon() { return lon; }
	public void setLon(double lon) { this.lon = lon; }
	public String getTel() { return tel; }
	public void setTel(String tel) { this.tel = tel; }
	public String getHomepage() { return homepage; }
	public void setHomepage(String homepage) { this.homepage = homepage; }
	public String getOverview() { return overview; }
	public void setOverview(String overview) { this.overview = overview; }
	public boolean isIndoor() { return indoor; }
	public void setIndoor(boolean indoor) { this.indoor = indoor; }
	public String getSidoCode() { return sidoCode; }
	public void setSidoCode(String sidoCode) { this.sidoCode = sidoCode; }
	public String getSigunguCode() { return sigunguCode; }
	public void setSigunguCode(String sigunguCode) { this.sigunguCode = sigunguCode; }
	public int getDistM() { return distM; }
	public void setDistM(int distM) { this.distM = distM; }
}
