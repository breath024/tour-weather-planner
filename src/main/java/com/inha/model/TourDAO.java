package com.inha.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// API ① 한국관광공사 국문 관광정보 서비스 (TourAPI, KorService2)
// ※ 과제 PDF 의 KorService1 은 폐기돼서 KorService2 를 쓴다
public class TourDAO {

	private static final String BASE = "https://apis.data.go.kr/B551011/KorService2/";
	private static final String API_NAME = "관광공사 TourAPI";
	private final String serviceKey;

	public TourDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	private String common() {
		return "serviceKey=" + serviceKey + "&MobileOS=ETC&MobileApp=TourWeather&_type=json";
	}

	// 지역기반 관광정보 조회: 시도(+시군구) · 관광타입으로 목록
	public List<TourVO> areaList(String sido, String sigungu, String contentTypeId, int rows) throws ApiException {
		String url = BASE + "areaBasedList2?" + common()
				+ "&numOfRows=" + rows + "&pageNo=1"
				+ "&arrange=Q"                 // 대표 이미지가 있는 것만, 수정일순
				+ "&contentTypeId=" + contentTypeId
				+ "&lDongRegnCd=" + sido;
		if (sigungu != null && !sigungu.isEmpty()) {
			url += "&lDongSignguCd=" + sigungu;
		}
		return toList(url, ApiClient.TTL_LIST);
	}

	// 키워드 검색: 제목에 키워드가 들어간 곳 (시도 · 시군구는 고른 경우에만 같이 보냄)
	public List<TourVO> keywordList(String keyword, String sido, String sigungu, String contentTypeId, int rows) throws ApiException {
		String url = BASE + "searchKeyword2?" + common()
				+ "&numOfRows=" + rows + "&pageNo=1"
				+ "&arrange=Q"
				+ "&contentTypeId=" + contentTypeId
				+ "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
		if (sido != null && !sido.isEmpty()) {
			url += "&lDongRegnCd=" + sido;
			if (sigungu != null && !sigungu.isEmpty()) {
				url += "&lDongSignguCd=" + sigungu;
			}
		}
		return toList(url, ApiClient.TTL_LIST);
	}

	// 위치기반 조회: 좌표 주변(반경 m)의 같은 타입 관광지를 가까운 순으로. 거리는 distM 에 담김
	public List<TourVO> nearby(double lat, double lon, int radiusM, String contentTypeId, int rows) throws ApiException {
		String url = BASE + "locationBasedList2?" + common()
				+ "&numOfRows=" + rows + "&pageNo=1"
				+ "&arrange=E"                 // 거리순
				+ (contentTypeId == null || contentTypeId.isEmpty() ? "" : "&contentTypeId=" + contentTypeId) // 빈 값이면 타입 없이
				+ "&mapX=" + lon + "&mapY=" + lat      // mapX = 경도, mapY = 위도
				+ "&radius=" + radiusM;
		return toList(url, ApiClient.TTL_LIST);
	}

	// 관광지(12) 결과와 문화시설(14) 결과를 거리순으로 합친다. 자기 자신(excludeId)은 뺀다
	public static List<TourVO> mergeByDist(List<TourVO> a, List<TourVO> b, String excludeId, int limit) {
		List<TourVO> all = new ArrayList<>();
		if (a != null) all.addAll(a);
		if (b != null) all.addAll(b);
		all.sort((x, y) -> Integer.compare(x.getDistM(), y.getDistM()));
		List<TourVO> result = new ArrayList<>();
		for (TourVO v : all) {
			if (v.getContentId().equals(excludeId)) continue;
			if (!v.isHasLocation()) continue;
			result.add(v);
			if (result.size() >= limit) break;
		}
		return result;
	}

	// 위치기반 조회(locationBasedList2)는 관광지(12) · 문화시설(14)이 거의 안 잡힌다(경주역 반경 5km 에서 1곳, 0곳).
	// 그래서 지역 목록(areaBasedList2)에서 직접 거리를 재서 반경 안의 곳을 보충한다.
	// pool 의 곳들에 거리(distM)를 채워서, 반경 안이고 이미 있는 곳이 아닌 것만 가까운 순으로 돌려준다
	public static List<TourVO> withinRadius(List<TourVO> pool, double lat, double lon, int radiusM, List<TourVO> already, String excludeId) {
		List<TourVO> result = new ArrayList<>();
		for (TourVO v : pool) {
			if (!v.isHasLocation() || v.getContentId().equals(excludeId)) continue;
			boolean dup = false;
			for (TourVO a : already) {
				if (a.getContentId().equals(v.getContentId())) dup = true;
			}
			if (dup) continue;
			int d = (int) Math.round(Geo.distanceKm(lat, lon, v.getLat(), v.getLon()) * 1000);
			if (d > radiusM) continue;
			v.setDistM(d);
			result.add(v);
		}
		return result;
	}

	// 이 날 열리고 있는 축제 · 행사 (searchFestival2). 시도(+시군구) 안에서 eventstartdate <= 날짜 <= eventenddate 인 것만.
	// 요청 변수(견본으로 확인): eventStartDate 가 필수, eventEndDate 도 넣으면 그 기간과 겹치는 행사만 온다
	// (eventEndDate 를 안 보내면 그 날 이후에 열릴 행사까지 전부 와서 쪽수가 모자란다)
	// 호출을 아끼려고 「오늘 ~ 10일 뒤」 기간을 지역마다 한 번만 받아 30분 캐시하고, 날짜는 서버가 걸러 고른다
	// → 같은 지역에서 날짜만 바꿔 보면 외부 호출이 0번이다
	public List<TourVO> festivals(String sido, String sigungu, LocalDate day, int rows) throws ApiException {
		LocalDate from = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
		String url = BASE + "searchFestival2?" + common()
				+ "&numOfRows=" + rows + "&pageNo=1"
				+ "&arrange=A"                  // 제목순
				+ "&eventStartDate=" + from.format(YMD) + "&eventEndDate=" + from.plusDays(Area.MAX_DAY).format(YMD)
				+ "&lDongRegnCd=" + sido;
		if (sigungu != null && !sigungu.isEmpty()) {
			url += "&lDongSignguCd=" + sigungu;
		}
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_LIST);
		checkResult(root);
		return parseFestivals(root, day, sigungu);
	}

	// 축제 응답 → 목록. 서버가 이미 걸렀지만 한 번 더: 기간이 그 날을 품은 행사만 · 시군구를 골랐으면 그 시군구만
	static List<TourVO> parseFestivals(JsonObject root, LocalDate day, String sigungu) {
		String d = day.format(YMD);
		List<TourVO> list = new ArrayList<>();
		JsonArray items = ApiClient.items(root);
		for (int i = 0; i < items.size(); i++) {
			TourVO v = toVO(items.get(i).getAsJsonObject());
			if (!v.getEventStart().isEmpty() && v.getEventStart().compareTo(d) > 0) continue; // 아직 시작 전
			if (!v.getEventEnd().isEmpty() && v.getEventEnd().compareTo(d) < 0) continue;     // 이미 끝남
			if (sigungu != null && !sigungu.isEmpty() && !v.getSigunguCode().isEmpty() && !v.getSigunguCode().equals(sigungu)) continue;
			v.setEventRefYear(day.getYear());
			list.add(v);
		}
		return list;
	}

	// 대분류 이름표(lclsSystmCode2): {코드 → 이름} 24시간 캐시. 못 불러오면 견본으로 확인한 기본 이름표를 쓴다 (이름이 없다고 화면이 깨지지 않게)
	public Map<String, String> lclsNames() {
		Map<String, String> map = new LinkedHashMap<>(LCLS_DEFAULT);
		// 방금(30분 안) 실패했으면 다시 부르지 않고 기본 이름표를 쓴다 (실패할 때마다 검색마다 호출이 늘지 않게)
		if (System.currentTimeMillis() < lclsFailUntil) return map;
		try {
			String url = BASE + "lclsSystmCode2?" + common() + "&numOfRows=100&pageNo=1&lclsSystmListYn=N";
			JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_CODE);
			checkResult(root);
			JsonArray items = ApiClient.items(root);
			for (int i = 0; i < items.size(); i++) {
				JsonObject o = items.get(i).getAsJsonObject();
				String code = ApiClient.str(o, "code");
				String name = ApiClient.str(o, "name");
				if (!code.isEmpty() && !name.isEmpty()) map.put(code, name);
			}
		} catch (Exception e) {
			lclsFailUntil = System.currentTimeMillis() + LCLS_FAIL_MILLIS;
			System.out.println("[TourWeather] 분류 이름표 조회 실패(기본 이름표 사용): " + e.getMessage());
		}
		return map;
	}

	private static final long LCLS_FAIL_MILLIS = 30 * 60 * 1000L;
	private static volatile long lclsFailUntil;   // 이 시각까지는 lclsSystmCode2 를 다시 부르지 않는다 (실패 뒤 쉬는 시간)
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final Map<String, String> LCLS_DEFAULT = new LinkedHashMap<>();
	static {
		LCLS_DEFAULT.put("AC", "숙박");
		LCLS_DEFAULT.put("C01", "추천코스");
		LCLS_DEFAULT.put("EV", "축제/공연/행사");
		LCLS_DEFAULT.put("EX", "체험관광");
		LCLS_DEFAULT.put("FD", "음식");
		LCLS_DEFAULT.put("HS", "역사관광");
		LCLS_DEFAULT.put("LS", "레저스포츠");
		LCLS_DEFAULT.put("NA", "자연관광");
		LCLS_DEFAULT.put("SH", "쇼핑");
		LCLS_DEFAULT.put("VE", "문화관광");
	}

	// 목록 응답 → TourVO 목록
	private List<TourVO> toList(String url, long ttl) throws ApiException {
		JsonObject root = ApiClient.getJson(url, API_NAME, ttl);
		checkResult(root);
		List<TourVO> list = new ArrayList<>();
		JsonArray items = ApiClient.items(root);
		for (int i = 0; i < items.size(); i++) {
			list.add(toVO(items.get(i).getAsJsonObject()));
		}
		return list;
	}

	// 공통정보 조회: 관광지 한 곳의 상세 (개요 · 홈페이지 · 좌표)
	public TourVO detail(String contentId) throws ApiException {
		String url = BASE + "detailCommon2?" + common() + "&contentId=" + contentId;
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_DETAIL);
		checkResult(root);
		JsonArray items = ApiClient.items(root);
		if (items.size() == 0) {
			return null;
		}
		JsonObject o = items.get(0).getAsJsonObject();
		TourVO vo = toVO(o);
		vo.setOverview(cleanText(ApiClient.str(o, "overview")));
		vo.setHomepage(extractUrl(ApiClient.str(o, "homepage")));
		return vo;
	}

	// 이 번호의 공통정보가 이미 캐시에 있나 (있으면 detail() 이 외부 호출을 하지 않는다)
	public boolean isDetailCached(String contentId) {
		return ApiClient.isCached(BASE + "detailCommon2?" + common() + "&contentId=" + contentId, ApiClient.TTL_DETAIL);
	}

	// 소개정보 조회(detailIntro2): "이용시간 → 10:00~18:00" 같은 {화면 이름 → 값} 표. 빈 칸은 뺀다
	// 관광 타입마다 API 칸 이름이 달라서 IntroFields 표로 옮긴다
	public Map<String, String> intro(String contentId, String contentTypeId) throws ApiException {
		Map<String, String> result = new LinkedHashMap<>();
		String[][] fields = IntroFields.of(contentTypeId);
		if (fields.length == 0) return result;
		String url = BASE + "detailIntro2?" + common() + "&contentId=" + contentId + "&contentTypeId=" + contentTypeId;
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_DETAIL);
		checkResult(root);
		JsonArray items = ApiClient.items(root);
		if (items.size() == 0) return result;
		JsonObject o = items.get(0).getAsJsonObject();
		for (String[] f : fields) {
			String v = introValue(ApiClient.str(o, f[0]));
			if (!v.isEmpty()) result.put(f[1], v);
		}
		return result;
	}

	// 소개정보 값 정리: <br> 정리 · 빈 줄 줄이기 · 날짜(행사 시작일 · 종료일 20261119)는 2026.11.19 로
	static String introValue(String raw) {
		String v = cleanText(raw).replaceAll("\n{2,}", "\n");
		if (v.matches("\\d{8}")) v = v.substring(0, 4) + "." + v.substring(4, 6) + "." + v.substring(6, 8);
		return v;
	}

	// 이미지 조회(detailImage2): 사진 갤러리 (최대 max 장)
	public List<ImageVO> images(String contentId, int max) throws ApiException {
		String url = BASE + "detailImage2?" + common() + "&contentId=" + contentId
				+ "&imageYN=Y&numOfRows=" + max + "&pageNo=1";
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_DETAIL);
		checkResult(root);
		List<ImageVO> list = new ArrayList<>();
		JsonArray items = ApiClient.items(root);
		for (int i = 0; i < items.size() && list.size() < max; i++) {
			JsonObject o = items.get(i).getAsJsonObject();
			String original = secure(ApiClient.str(o, "originimgurl"));
			if (original.isEmpty()) continue;
			ImageVO img = new ImageVO();
			img.setOriginal(original);
			String small = secure(ApiClient.str(o, "smallimageurl"));
			img.setSmall(small.isEmpty() ? original : small);
			img.setName(cleanText(ApiClient.str(o, "imgname")));
			list.add(img);
		}
		return list;
	}

	// 법정동 코드 조회: 시도 안의 시군구 목록 [코드, 이름]
	// 실패해도 검색은 시도 단위로 되므로 빈 목록을 돌려준다
	public List<String[]> sigunguList(String sido) {
		List<String[]> list = new ArrayList<>();
		try {
			String url = BASE + "ldongCode2?" + common() + "&numOfRows=100&pageNo=1&lDongRegnCd=" + sido;
			JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_CODE);
			JsonArray items = ApiClient.items(root);
			for (int i = 0; i < items.size(); i++) {
				JsonObject o = items.get(i).getAsJsonObject();
				String code = ApiClient.str(o, "code");
				String name = ApiClient.str(o, "name");
				if (code.isEmpty()) code = ApiClient.str(o, "lDongSignguCd");
				if (name.isEmpty()) name = ApiClient.str(o, "lDongSignguNm");
				// 세종처럼 시군구가 없으면 자기 자신(시도 코드) 하나만 온다 → 빼기
				if (!code.isEmpty() && !name.isEmpty() && !code.equals(sido)) {
					list.add(new String[] { code, name });
				}
			}
		} catch (Exception e) {
			System.out.println("[TourWeather] 시군구 목록 조회 실패: " + e.getMessage());
		}
		return withoutParentCities(list);
	}

	// 구가 있는 시(전주 110 · 수원 110 · 성남 130 …)는 상위 코드로 관광지를 찾으면 0건이다 (관광지는 하위 구 코드 111 · 113 … 에 들어 있다).
	// 같은 앞 두 자리를 쓰는 하위 코드(끝이 0이 아닌 코드)가 있는 상위 코드는 목록에서 뺀다. 구를 골라야 관광지 · 붐빔이 나온다
	static List<String[]> withoutParentCities(List<String[]> list) {
		List<String[]> out = new ArrayList<>();
		for (String[] item : list) {
			String code = item[0];
			boolean parent = false;
			if (code.matches("\\d{2}0")) {
				for (String[] other : list) {
					String c = other[0];
					if (c.matches("\\d{3}") && c.startsWith(code.substring(0, 2)) && !c.equals(code) && c.charAt(2) != '0') {
						parent = true;
						break;
					}
				}
			}
			if (!parent) out.add(item);
		}
		return out;
	}

	private void checkResult(JsonObject root) throws ApiException {
		String code = ApiClient.resultCode(root);
		if (!code.isEmpty() && !code.equals("0000") && !code.equals("00")) {
			throw new ApiException(API_NAME + " 오류: " + ApiClient.resultMsg(root) + " (" + code + ")");
		}
	}

	private static TourVO toVO(JsonObject o) {
		TourVO vo = new TourVO();
		vo.setContentId(ApiClient.str(o, "contentid"));
		vo.setContentTypeId(ApiClient.str(o, "contenttypeid"));
		vo.setTypeName(typeName(vo.getContentTypeId()));
		vo.setTitle(ApiClient.str(o, "title"));
		String addr = ApiClient.str(o, "addr1");
		String addr2 = ApiClient.str(o, "addr2");
		if (!addr2.isEmpty()) addr += " " + addr2;
		vo.setAddr(addr);
		vo.setImage(secure(ApiClient.str(o, "firstimage")));
		vo.setLat(ApiClient.num(o, "mapy")); // mapy = 위도
		vo.setLon(ApiClient.num(o, "mapx")); // mapx = 경도
		vo.setTel(cleanText(ApiClient.str(o, "tel")));
		vo.setIndoor(Recommender.isIndoor(vo.getContentTypeId(), vo.getTitle()));
		vo.setSidoCode(ApiClient.str(o, "lDongRegnCd"));
		vo.setSigunguCode(ApiClient.str(o, "lDongSignguCd"));
		vo.setDistM((int) Math.round(ApiClient.num(o, "dist"))); // 위치기반 조회에서만 있음
		vo.setLclsCode(ApiClient.str(o, "lclsSystm1"));          // 대분류 코드 (이름은 lclsNames 로)
		vo.setLcls3Code(ApiClient.str(o, "lclsSystm3"));         // 소분류 코드 (행사 실내 · 야외를 가를 때)
		if ("15".equals(vo.getContentTypeId())) {
			int place = Recommender.eventPlace(vo.getLcls3Code(), vo.getTitle());   // 행사는 제목 말만 보지 않고 분류도 본다
			vo.setIndoor(place == 1);
			vo.setPlaceUnknown(place < 0);
		}
		vo.setEventStart(ApiClient.str(o, "eventstartdate"));    // 축제 · 행사 검색에서만 있음
		vo.setEventEnd(ApiClient.str(o, "eventenddate"));
		return vo;
	}

	public static String typeName(String contentTypeId) {
		switch (contentTypeId) {
		case "12": return "관광지";
		case "14": return "문화시설";
		case "15": return "축제·행사";
		case "25": return "여행코스";
		case "28": return "레포츠";
		case "32": return "숙박";
		case "38": return "쇼핑";
		case "39": return "음식점";
		default: return "기타";
		}
	}

	// 관광공사 사진 주소가 http 로 올 때가 있다 → https 로 (https 페이지에서 막히지 않게)
	private static String secure(String url) {
		if (url != null && url.startsWith("http://tong.visitkorea.or.kr/")) {
			return "https://" + url.substring("http://".length());
		}
		return url == null ? "" : url;
	}

	// 개요 · 전화번호에 섞인 <br> 같은 태그를 줄바꿈/빈칸으로 정리
	private static String cleanText(String s) {
		if (s == null) return "";
		s = s.replaceAll("(?i)<br\\s*/?>", "\n");
		s = s.replaceAll("<[^>]*>", "");
		s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
				.replace("&quot;", "\"").replace("&amp;", "&");
		return s.trim();
	}

	// homepage 는 <a href="...">...</a> 형태로 온다 → 주소만 뽑기
	private static String extractUrl(String s) {
		if (s == null || s.isEmpty()) return "";
		Matcher m = Pattern.compile("href=[\"']?(https?://[^\"'\\s>]+)").matcher(s);
		if (m.find()) return m.group(1);
		m = Pattern.compile("(https?://[^\"'\\s<>]+)").matcher(s);
		if (m.find()) return m.group(1);
		return "";
	}

}
