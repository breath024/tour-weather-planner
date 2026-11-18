package com.inha.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

// 지역 코드 변환표 (2판): 관광공사 TourAPI 시도 코드(Area.java 의 16개) 하나를 다른 API 들이 쓰는 코드로 바꾼다.
//  - 기상청 중기예보: 육상 regId(예: 11B00000) + 기온 regId(대표 도시 하나)
//  - 에어코리아: 실시간 sidoName(한글 짧은 이름) + 예보 지역 이름(informGrade 에 나오는 이름)
//  - 기상청 생활기상지수(자외선): areaNo 10자리
//  - 관광지 집중률: areaCd / signguCd
//  - 기상특보 문장에 나오는 시도 이름
//
// 2026-11-18 실측 (이 날 실제 호출로 확인한 것은 [확인], 기억이나 문서로만 아는 것은 [기억] 으로 표시)
//  [확인] 중기 육상 regId 10개 모두 응답 옴: 11B00000 11D10000 11D20000 11C20000 11C10000 11F20000 11F10000 11H10000 11H20000 11G00000
//  [확인] 중기 기온 regId 는 아래 표의 것이 모두 응답 옴. 단 목포 11F20801 은 NO_DATA(03) 라 광주 11F20501 을 대표로 썼다.
//         (도시 이름은 [기억] — 응답으로는 "코드가 유효하다"까지만 확인했다)
//  [확인] 에어코리아 sidoName 17개(서울 부산 대구 인천 광주 대전 울산 경기 강원 충북 충남 전북 전남 경북 경남 제주 세종)
//         모두 측정소가 옴. 광주 · 전남은 합쳐진 뒤에도 따로 오므로 12(광주·전남)는 두 곳을 모두 부른다.
//         TourAPI 시군구가 광주의 5개 구(210 동구 · 240 서구 · 270 남구 · 300 북구 · 330 광산구)면 "광주", 나머지는 "전남".
//  [확인] 에어코리아 예보(informGrade)는 "서울 : 보통,…,영동 : 좋음,영서 : 보통,경기남부 : …,경기북부 : …" 모양이라
//         강원은 영동 · 영서, 경기는 경기남부 · 경기북부로 나뉜다.
//  [확인] 자외선 areaNo = 시도(2자리) + 시군구(3자리) + "00000". 시도만이면 시도 + "00000000".
//         새 코드(강원 51 · 전북 52 · 광주전남 12)가 통한다. 옛 코드(42 · 45 · 46 · 29)는 "검색결과가 없습니다".
//         세종은 3611000000 (시도 36110 → 앞 5자리가 시도 + 시군구 둘을 겸함).
//         시군구 단위도 통했다: 4713000000 경주 · 5111000000 춘천 · 5211000000 전주 · 1221000000 광주 동구 · 1271000000 담양 · 1287000000 신안.
//         (없는 시군구는 resultCode 99 → UvDAO 가 시도 단위로 한 번 더 부른다)
//  [확인] 집중률 areaCd = 시도 2자리(세종만 36), signguCd = 시도 + TourAPI 시군구 3자리(세종만 36110). signguCd 는 필수.
//         서울 11/11110 · 부산 26/26110 · 경북 47/47130 · 제주 50/50110 · 강원 51/51150 · 전북 52/52111 · 군산 52/52130 · 세종 36/36110 이 자료가 나옴.
//         전주시 전체(52110)는 자료 0건, 완산구(52111) · 덕진구(52113)로 나눠야 나온다.
//         세종은 areaCd=36110 이면 0건, 36 이어야 나온다.
//         주의: 광주 · 전남은 자료가 하나도 없다: areaCd 12 · 29 · 46 에 시군구 12종을 넣어 봐도 전부 totalCount 0 (HTTP 200, 오류 아님)
//           → 12 는 "붐빔 자료 없음"으로 다룬다. 나머지 시도(대구 · 인천 · 대전 · 울산 · 경기 · 충북 · 충남 · 경남)는 같은 규칙이라고 [기억]만 한다(직접 안 불러 봄).
//  [확인] 기상특보 문장(t6 · t7)은 시도를 "강원도 · 전북자치도 · 전라남도 · 경상북도 · 제주도 · 인천 · 서해5도 · 울릉도.독도 · 광주" 처럼 쓴다. (WarningDAO 참고)
public class RegionCodes {

	// 시도 하나의 변환 결과
	public static class Region {
		private final String tourCode;       // TourAPI 시도 코드 (lDongRegnCd)
		private final String name;           // 짧은 이름
		private final String midLandRegId;   // 중기 육상 regId (강원은 영서 기본, 영동은 midLandRegId() 에서 바꿈)
		private final String midTaRegId;     // 중기 기온 regId (대표 도시 하나)
		private final String midTaCity;      // 그 대표 도시 이름 [기억]
		private final String[] airNow;       // 에어코리아 실시간 sidoName
		private final String[] airForecast;  // 에어코리아 예보 지역 이름
		private final String uvAreaNo;       // 자외선 areaNo 중 시도 단위
		private final String crowdAreaCd;    // 집중률 areaCd (자료가 없는 시도는 "")
		private final String[] warnNames;    // 기상특보 문장에 나오는 시도 이름

		Region(String tourCode, String name, String midLandRegId, String midTaRegId, String midTaCity,
				String[] airNow, String[] airForecast, String uvAreaNo, String crowdAreaCd, String[] warnNames) {
			this.tourCode = tourCode;
			this.name = name;
			this.midLandRegId = midLandRegId;
			this.midTaRegId = midTaRegId;
			this.midTaCity = midTaCity;
			this.airNow = airNow;
			this.airForecast = airForecast;
			this.uvAreaNo = uvAreaNo;
			this.crowdAreaCd = crowdAreaCd;
			this.warnNames = warnNames;
		}

		public String getTourCode() { return tourCode; }
		public String getName() { return name; }
		public String getMidLandRegId() { return midLandRegId; }
		public String getMidTaRegId() { return midTaRegId; }
		public String getMidTaCity() { return midTaCity; }
		public String[] getAirNow() { return airNow.clone(); }
		public String[] getAirForecast() { return airForecast.clone(); }
		public String getUvAreaNo() { return uvAreaNo; }
		public String getCrowdAreaCd() { return crowdAreaCd; }
		public String[] getWarnNames() { return warnNames.clone(); }
	}

	private static final Map<String, Region> TABLE = new LinkedHashMap<>();

	private static void add(String code, String name, String land, String ta, String taCity,
			String[] airNow, String[] airFcst, String uv, String crowd, String[] warn) {
		TABLE.put(code, new Region(code, name, land, ta, taCity, airNow, airFcst, uv, crowd, warn));
	}

	private static String[] a(String... names) {
		return names;
	}

	static { // Area.java 의 16개와 같은 순서
		add("11", "서울", "11B00000", "11B10101", "서울", a("서울"), a("서울"), "1100000000", "11", a("서울"));
		add("26", "부산", "11H20000", "11H20201", "부산", a("부산"), a("부산"), "2600000000", "26", a("부산"));
		add("27", "대구", "11H10000", "11H10701", "대구", a("대구"), a("대구"), "2700000000", "27", a("대구"));
		add("28", "인천", "11B00000", "11B20201", "인천", a("인천"), a("인천"), "2800000000", "28", a("인천", "서해5도"));
		add("30", "대전", "11C20000", "11C20401", "대전", a("대전"), a("대전"), "3000000000", "30", a("대전"));
		add("31", "울산", "11H20000", "11H20101", "울산", a("울산"), a("울산"), "3100000000", "31", a("울산"));
		add("36110", "세종", "11C20000", "11C20404", "세종", a("세종"), a("세종"), "3611000000", "36", a("세종"));
		add("41", "경기", "11B00000", "11B20601", "수원", a("경기"), a("경기남부", "경기북부"), "4100000000", "41", a("경기도", "경기"));
		add("51", "강원", "11D10000", "11D10301", "춘천", a("강원"), a("영동", "영서"), "5100000000", "51", a("강원도", "강원특별자치도"));
		add("43", "충북", "11C10000", "11C10301", "청주", a("충북"), a("충북"), "4300000000", "43", a("충청북도", "충북"));
		add("44", "충남", "11C20000", "11C20301", "천안", a("충남"), a("충남"), "4400000000", "44", a("충청남도", "충남"));
		add("52", "전북", "11F10000", "11F10201", "전주", a("전북"), a("전북"), "5200000000", "52", a("전북자치도", "전북특별자치도", "전라북도"));
		add("12", "광주·전남", "11F20000", "11F20501", "광주", a("광주", "전남"), a("광주", "전남"), "1200000000", "", a("광주", "전라남도", "전남"));
		add("47", "경북", "11H10000", "11H10501", "안동", a("경북"), a("경북"), "4700000000", "47", a("경상북도", "울릉도.독도"));
		add("48", "경남", "11H20000", "11H20301", "창원", a("경남"), a("경남"), "4800000000", "48", a("경상남도"));
		add("50", "제주", "11G00000", "11G00201", "제주", a("제주"), a("제주"), "5000000000", "50", a("제주도", "제주특별자치도"));
	}

	// 광주광역시의 5개 구 (TourAPI 시도 12 아래 시군구 코드). 나머지는 전남
	private static final String[] GWANGJU_GU = { "210", "240", "270", "300", "330" };
	// 강원 영동 시군구: 강릉 150 · 동해 170 · 태백 190 · 속초 210 · 삼척 230 · 고성 820 · 양양 830 [기억] (나머지는 영서)
	private static final String[] GANGWON_YEONGDONG = { "150", "170", "190", "210", "230", "820", "830" };

	// 시군구별 중기 기온 regId (시도 대표 도시보다 가까운 도시가 있는 곳). 키 = 시도 코드 + 시군구 코드
	// 2026-11-18 getMidTa 직접 호출로 [확인]: 아래 regId 가 모두 응답하고 경주 · 포항이 안동과 최대 5° 달랐다.
	// 도시 이름은 [기억] (기상청 중기예보 구역표 · 응답에는 이름이 없다)
	private static final Map<String, String[]> TA_BY_SIGUNGU = new LinkedHashMap<>();
	static {
		taOf("47", new String[] { "110", "111", "113" }, "11H10201", "포항");
		taOf("47", new String[] { "130" }, "11H10202", "경주");
		taOf("47", new String[] { "930" }, "11H10101", "울진");
		taOf("50", new String[] { "130" }, "11G00401", "서귀포");
		taOf("12", new String[] { "130" }, "11F20401", "여수");
		taOf("12", new String[] { "150" }, "11F20402", "순천");
	}

	private static void taOf(String sido, String[] sigungus, String regId, String city) {
		for (String g : sigungus) TA_BY_SIGUNGU.put(sido + g, new String[] { regId, city });
	}

	public static Collection<Region> all() {
		return TABLE.values();
	}

	// 시도 코드 → 변환표 한 줄 (모르는 코드면 null)
	public static Region of(String sido) {
		return sido == null ? null : TABLE.get(sido);
	}

	private static boolean in(String code, String[] list) {
		for (String s : list) {
			if (s.equals(code)) return true;
		}
		return false;
	}

	private static boolean has(String sigungu) {
		return sigungu != null && !sigungu.isEmpty();
	}

	// ---- 기상청 중기예보 ----

	// 중기 육상 regId. 강원은 영동 시군구면 영동(11D20000), 아니면 영서(11D10000)
	public static String midLandRegId(String sido, String sigungu) {
		Region r = of(sido);
		if (r == null) return null;
		if (sido.equals("51") && in(sigungu, GANGWON_YEONGDONG)) return "11D20000";
		return r.midLandRegId;
	}

	// 중기 기온 regId. 시군구 표(TA_BY_SIGUNGU)에 있으면 그 도시, 없으면 시도 대표 도시 하나.
	// 강원 영동은 강릉(11D20501), 영서는 춘천(11D10301)
	public static String midTaRegId(String sido, String sigungu) {
		Region r = of(sido);
		if (r == null) return null;
		String[] own = TA_BY_SIGUNGU.get(sido + (sigungu == null ? "" : sigungu));
		if (own != null) return own[0];
		if (sido.equals("51") && in(sigungu, GANGWON_YEONGDONG)) return "11D20501";
		return r.midTaRegId;
	}

	// 중기 기온을 어느 도시 기준으로 보여 주는지 (화면에 밝힌다). 모르는 시도면 ""
	public static String midTaCity(String sido, String sigungu) {
		Region r = of(sido);
		if (r == null) return "";
		String[] own = TA_BY_SIGUNGU.get(sido + (sigungu == null ? "" : sigungu));
		if (own != null) return own[1];
		if (sido.equals("51") && in(sigungu, GANGWON_YEONGDONG)) return "강릉";
		return r.midTaCity;
	}

	// ---- 에어코리아 ----

	// 실시간 sidoName 목록. 광주·전남(12)은 시군구를 알면 한쪽만, 모르면 둘 다
	public static String[] airNowNames(String sido, String sigungu) {
		Region r = of(sido);
		if (r == null) return new String[0];
		if (sido.equals("12") && has(sigungu)) {
			return in(sigungu, GWANGJU_GU) ? a("광주") : a("전남");
		}
		return r.getAirNow();
	}

	// 예보 지역 이름 목록 (informGrade 의 "서울 : 보통" 앞부분). 강원은 시군구를 알면 영동/영서 한쪽
	public static String[] airForecastNames(String sido, String sigungu) {
		Region r = of(sido);
		if (r == null) return new String[0];
		if (sido.equals("12") && has(sigungu)) {
			return in(sigungu, GWANGJU_GU) ? a("광주") : a("전남");
		}
		if (sido.equals("51") && has(sigungu)) {
			return in(sigungu, GANGWON_YEONGDONG) ? a("영동") : a("영서");
		}
		return r.getAirForecast();
	}

	// ---- 자외선 ----

	// areaNo 10자리: 시군구를 알면 시도 + 시군구 + "00000", 모르면 시도 단위
	public static String uvAreaNo(String sido, String sigungu) {
		Region r = of(sido);
		if (r == null) return null;
		if (has(sigungu) && sigungu.matches("\\d{3}") && !sido.equals("36110")) {
			return sido + sigungu + "00000";
		}
		return r.uvAreaNo;
	}

	// 시도 단위 areaNo (시군구 단위가 없을 때 되돌아가는 값)
	public static String uvSidoAreaNo(String sido) {
		Region r = of(sido);
		return r == null ? null : r.uvAreaNo;
	}

	// ---- 관광지 집중률 ----

	// 집중률 자료가 있는 시도인가 (광주·전남은 없음)
	public static boolean crowdSupported(String sido) {
		Region r = of(sido);
		return r != null && !r.crowdAreaCd.isEmpty();
	}

	public static String crowdAreaCd(String sido) {
		Region r = of(sido);
		return r == null || r.crowdAreaCd.isEmpty() ? null : r.crowdAreaCd;
	}

	// signguCd = 시도 2자리 + TourAPI 시군구 3자리 (세종만 36110). 시군구를 모르면 null (이 API 는 signguCd 가 필수)
	public static String crowdSignguCd(String sido, String sigungu) {
		if (!crowdSupported(sido)) return null;
		if (sido.equals("36110")) return "36110";
		if (!has(sigungu) || !sigungu.matches("\\d{3}")) return null;
		return sido + sigungu;
	}

	// ---- 기상특보 ----

	// 특보 문장에 나오는 시도 이름들 (정식 · 짧은 이름). 시군구를 모르면 시도 전체
	public static String[] warnNames(String sido) {
		Region r = of(sido);
		return r == null ? new String[0] : r.getWarnNames();
	}

	// 광주 · 전남(12)은 특보 문장에서 "광주" 와 "전라남도" 가 따로 나온다 → 시군구가 광주의 구면 "광주", 아니면 전남 이름만.
	// (안 그러면 전남 해안 군의 특보가 광주 구에 발효 중으로 뜬다)
	public static String[] warnNames(String sido, String sigungu) {
		if (sido != null && sido.equals("12") && has(sigungu)) {
			return in(sigungu, GWANGJU_GU) ? a("광주") : a("전라남도", "전남");
		}
		return warnNames(sido);
	}
}
