package com.inha.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// API ③ 에어코리아 대기오염정보 (ArpltnInforInqireSvc)
//  - getCtprvnRltmMesureDnsty : 시도별 실시간 측정값 (측정소 평균을 낸다)
//  - getMinuDustFrcstDspth    : 미세먼지 예보 (오늘 · 내일 · 모레, 지역별 등급)
// 하루 한도가 500회로 가장 빡빡하다 → 한도 초과 응답이 오면 그날은 더 부르지 않는다 (resting)
public class AirDAO {

	private static final String BASE = "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/";
	private static final String API_NAME = "에어코리아 대기오염정보";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MIN_STATIONS = 2; // 시군구 이름으로 측정소를 고르려면 최소 이만큼 맞아야 한다

	// 한도 초과 응답을 받은 날 (그날은 쉰다). 서버를 다시 띄우면 잊는다
	private static volatile LocalDate restingDay = null;

	private final String serviceKey;

	public AirDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	// 오늘은 에어코리아를 쉬는 중인가
	public static boolean isRestingToday() {
		return LocalDate.now(KST).equals(restingDay);
	}

	// 실시간 대기: sidoNames 의 측정소들을 모아 평균을 낸다 (광주·전남이면 두 곳을 모두).
	// stationKeyword(예: "경주")가 들어간 측정소가 2곳 이상이면 그 측정소들만, 아니면 시도 전체 평균
	// 값이 "-" 거나 Flag(점검 · 통신장애 등)가 붙은 측정값은 뺀다
	public AirVO realtime(String[] sidoNames, String stationKeyword) throws ApiException {
		if (sidoNames == null || sidoNames.length == 0) {
			throw new ApiException("이 지역의 대기오염 정보는 아직 지원하지 않아요.");
		}
		List<JsonObject> rows = new ArrayList<>();
		ApiException firstError = null;
		for (String name : sidoNames) {
			try {
				String url = BASE + "getCtprvnRltmMesureDnsty?serviceKey=" + serviceKey
						+ "&returnType=json&numOfRows=300&pageNo=1&ver=1.0&sidoName="
						+ URLEncoder.encode(name, StandardCharsets.UTF_8);
				JsonArray items = ApiClient.items(get(url, ApiClient.TTL_AIR_NOW));
				for (int i = 0; i < items.size(); i++) rows.add(items.get(i).getAsJsonObject());
			} catch (ApiException e) {
				if (firstError == null) firstError = e; // 한쪽만 실패해도 나머지로 계산
			}
		}
		if (rows.isEmpty()) {
			if (firstError != null) throw firstError;
			throw new ApiException("측정소 자료가 아직 없어요.");
		}
		return summarize(rows, sidoNames, stationKeyword);
	}

	// 측정소 줄들 → 평균 (네트워크 없이 시험할 수 있게 따로 뺐다)
	static AirVO summarize(List<JsonObject> rows, String[] sidoNames, String stationKeyword) {
		// 시군구 이름이 들어간 측정소만 고르기. 측정소 이름은 대부분 동 이름이라 맞는 곳이 적고,
		// 하나만 맞거나 항만 측정소("포항항" · "제주항")뿐이면 그 시군구 값이라고 하기 어렵다 → 2곳 이상 맞을 때만 쓰고, 아니면 시도 전체 평균
		boolean matched = false;
		List<JsonObject> use = rows;
		if (stationKeyword != null && !stationKeyword.isEmpty()) {
			List<JsonObject> picked = new ArrayList<>();
			for (JsonObject r : rows) {
				String name = ApiClient.str(r, "stationName");
				if (name.contains(stationKeyword) && !name.endsWith("항")) picked.add(r);
			}
			if (picked.size() >= MIN_STATIONS) {
				use = picked;
				matched = true;
			}
		}

		if (matched && !hasValue(use)) { // 고른 측정소들이 모두 점검 · 통신장애면 시도 평균으로 돌아간다
			use = rows;
			matched = false;
		}

		double sum10 = 0, sum25 = 0;
		int n10 = 0, n25 = 0;
		String latest = "";
		for (JsonObject r : use) {
			Integer v10 = value(r, "pm10Value", "pm10Flag");
			Integer v25 = value(r, "pm25Value", "pm25Flag");
			if (v10 != null) { sum10 += v10; n10++; }
			if (v25 != null) { sum25 += v25; n25++; }
			String t = ApiClient.str(r, "dataTime");
			if (t.compareTo(latest) > 0) latest = t;
		}

		AirVO vo = new AirVO();
		vo.setLabel(matched ? stationKeyword : String.join("·", sidoNames));
		vo.setDataTime(latest);
		vo.setStationCount(Math.max(n10, n25));
		vo.setStationMatched(matched);
		if (n10 > 0) {
			vo.setPm10((int) Math.round(sum10 / n10));
			vo.setPm10Grade(AirVO.gradeOfPm10(vo.getPm10()));
		}
		if (n25 > 0) {
			vo.setPm25((int) Math.round(sum25 / n25));
			vo.setPm25Grade(AirVO.gradeOfPm25(vo.getPm25()));
		}
		return vo;
	}

	private static boolean hasValue(List<JsonObject> list) {
		for (JsonObject r : list) {
			if (value(r, "pm10Value", "pm10Flag") != null || value(r, "pm25Value", "pm25Flag") != null) return true;
		}
		return false;
	}

	// 측정값 한 칸: "-" · 빈 값 · 숫자가 아님 · Flag 가 있으면 null (제외)
	private static Integer value(JsonObject r, String valueKey, String flagKey) {
		if (!ApiClient.str(r, flagKey).isEmpty()) return null;
		String s = ApiClient.str(r, valueKey);
		if (s.isEmpty() || s.equals("-")) return null;
		try {
			return (int) Math.round(Double.parseDouble(s));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// 미세먼지 예보: regionNames(예: {"영동","영서"})의 날짜별 등급 (오늘부터, 날짜 순).
	// 한 번 불러서 PM10 · PM2.5 가 모두 온다. 같은 날 여러 번 발표되면 가장 늦은 발표만 쓴다
	public List<AirForecastVO> forecast(String[] regionNames) throws ApiException {
		if (regionNames == null || regionNames.length == 0) {
			throw new ApiException("이 지역의 미세먼지 예보는 아직 지원하지 않아요.");
		}
		LocalDate today = LocalDate.now(KST);
		JsonArray items = forecastRows(today);
		if (items.size() == 0) items = forecastRows(today.minusDays(1)); // 새벽에 오늘 자료가 아직 없을 때

		// (PM10|PM25, 날짜) 별로 가장 늦은 발표 한 줄만 고른다
		Map<String, JsonObject> latest = new HashMap<>();
		for (int i = 0; i < items.size(); i++) {
			JsonObject it = items.get(i).getAsJsonObject();
			String key = ApiClient.str(it, "informCode") + "|" + ApiClient.str(it, "informData");
			JsonObject old = latest.get(key);
			if (old == null || ApiClient.str(it, "dataTime").compareTo(ApiClient.str(old, "dataTime")) > 0) {
				latest.put(key, it);
			}
		}

		Map<LocalDate, AirForecastVO> byDate = new TreeMap<>();
		for (Map.Entry<String, JsonObject> e : latest.entrySet()) {
			JsonObject it = e.getValue();
			LocalDate d;
			try {
				d = LocalDate.parse(ApiClient.str(it, "informData"));
			} catch (Exception ex) {
				continue;
			}
			if (d.isBefore(today)) continue;
			AirForecastVO vo = byDate.computeIfAbsent(d, k -> {
				AirForecastVO v = new AirForecastVO();
				v.setDate(k);
				return v;
			});
			int grade = gradeFor(ApiClient.str(it, "informGrade"), regionNames);
			if (ApiClient.str(it, "informCode").equals("PM10")) {
				vo.setPm10Grade(grade);
			} else {
				vo.setPm25Grade(grade);
			}
			vo.setIssued(ApiClient.str(it, "dataTime"));
			vo.setCause(ApiClient.str(it, "informCause"));
			vo.setOverall(ApiClient.str(it, "informOverall"));
		}
		if (byDate.isEmpty()) {
			throw new ApiException("미세먼지 예보가 아직 없어요.");
		}
		return new ArrayList<>(byDate.values());
	}

	private JsonArray forecastRows(LocalDate searchDate) throws ApiException {
		String url = BASE + "getMinuDustFrcstDspth?serviceKey=" + serviceKey
				+ "&returnType=json&numOfRows=50&pageNo=1&searchDate=" + searchDate;
		return ApiClient.items(get(url, ApiClient.TTL_AIR_FCST));
	}

	// "서울 : 보통,제주 : 좋음,영동 : 좋음,…" 에서 regionNames 에 해당하는 등급 중 가장 나쁜 것 (없으면 0)
	static int gradeFor(String informGrade, String[] regionNames) {
		int worst = 0;
		for (String part : informGrade.split(",")) {
			int c = part.indexOf(':');
			if (c < 0) continue;
			String region = part.substring(0, c).trim();
			for (String want : regionNames) {
				if (region.equals(want)) worst = Math.max(worst, gradeNo(part.substring(c + 1).trim()));
			}
		}
		return worst;
	}

	private static int gradeNo(String text) {
		switch (text) {
		case "좋음": return 1;
		case "보통": return 2;
		case "나쁨": return 3;
		case "매우나쁨": return 4;
		default: return 0;
		}
	}

	// 에어코리아 호출 공통: 쉬는 날이면 바로 거절, 한도 초과 응답이면 그날 쉬기 시작, 정상 코드(00)가 아니면 오류
	private JsonObject get(String url, long ttl) throws ApiException {
		if (isRestingToday()) {
			throw new ApiException("대기오염 정보는 오늘 조회 한도를 다 써서 내일 다시 볼 수 있어요.", true);
		}
		try {
			JsonObject root = ApiClient.getJson(url, API_NAME, ttl);
			String code = ApiClient.resultCode(root);
			if (!code.equals("00")) {
				String msg = ApiClient.resultMsg(root);
				if (code.equals("22") || ApiClient.isLimitMsg(msg)) { // 22 = 서비스 요청 제한 횟수 초과
					throw new ApiException("오늘 대기오염 정보 조회 횟수 한도를 넘었어요. 내일 다시 시도해 주세요.", true);
				}
				throw new ApiException(API_NAME + " 오류: " + msg + " (" + code + ")");
			}
			return root;
		} catch (ApiException e) {
			if (e.isQuotaExceeded()) restingDay = LocalDate.now(KST);
			throw e;
		}
	}
}
