package com.inha.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// API ⑤ 관광지 집중률 방문자 추이 예측 (TatsCnctrRateService / tatsCnctrRatedList)
// 시군구 하나의 관광지들 × 오늘부터 약 30일치가 한 번에 온다 (경주 69곳 × 30일 = 2070행, 약 260KB).
// 크기가 커서 ApiClient 의 문자열 캐시에는 넣지 않고(ttl 0), 읽어서 줄인 CrowdVO 를 여기서 6시간 캐시한다.
// signguCd 는 꼭 필요하고, 광주·전남은 자료가 없다 (RegionCodes 주석 참고).
public class CrowdDAO {

	private static final String URL = "https://apis.data.go.kr/B551011/TatsCnctrRateService/tatsCnctrRatedList";
	private static final String API_NAME = "관광지 집중률";
	private static final int ROWS = 3000;        // 한 번에 받는 행 수 (제주시처럼 더 많으면 앞부분만 쓰고 truncated 표시)
	private static final int CACHE_MAX = 40;

	// 읽어서 줄인 결과의 작은 캐시 (키 = areaCd|signguCd). 오류는 넣지 않는다
	private static final Map<String, Object[]> cache = new LinkedHashMap<>() {
		private static final long serialVersionUID = 1L;
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) {
			return size() > CACHE_MAX;
		}
	};

	private final String serviceKey;

	public CrowdDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	// 시도 코드 + TourAPI 시군구 코드로 붐빔 예측을 가져온다.
	// 자료가 없는 지역 · 시군구 미선택이면 예외 대신 available=false 인 CrowdVO (화면은 "자료 없음" 칩)
	public CrowdVO forecast(String sido, String sigungu) throws ApiException {
		if (!RegionCodes.crowdSupported(sido)) {
			return CrowdVO.unavailable("이 지역은 붐빔 예측 자료가 아직 없어요.");
		}
		String areaCd = RegionCodes.crowdAreaCd(sido);
		String signguCd = RegionCodes.crowdSignguCd(sido, sigungu);
		if (signguCd == null) {
			return CrowdVO.unavailable("시·군·구를 고르면 붐빔 예측을 볼 수 있어요.");
		}

		String key = areaCd + "|" + signguCd;
		synchronized (cache) {
			Object[] hit = cache.get(key);
			if (hit != null) {
				if ((Long) hit[1] > System.currentTimeMillis()) return (CrowdVO) hit[0];
				cache.remove(key);
			}
		}

		String url = URL + "?serviceKey=" + serviceKey + "&numOfRows=" + ROWS + "&pageNo=1"
				+ "&MobileOS=ETC&MobileApp=TourWeather&_type=json&areaCd=" + areaCd + "&signguCd=" + signguCd;
		JsonObject root = ApiClient.getJson(url, API_NAME, 0); // 크니까 문자열 캐시는 건너뛴다
		String code = ApiClient.resultCode(root);
		if (!code.equals("0000")) {
			throw new ApiException(API_NAME + " 오류: " + ApiClient.resultMsg(root) + " (" + code + ")");
		}

		CrowdVO vo = new CrowdVO();
		JsonArray items = ApiClient.items(root);
		for (int i = 0; i < items.size(); i++) {
			JsonObject it = items.get(i).getAsJsonObject();
			String site = ApiClient.str(it, "tAtsNm");
			String ymd = ApiClient.str(it, "baseYmd");
			String rate = ApiClient.str(it, "cnctrRate");
			if (site.isEmpty() || ymd.isEmpty() || rate.isEmpty()) continue;
			try {
				vo.put(site, ymd, Double.parseDouble(rate));
			} catch (NumberFormatException e) {
				// 숫자가 아닌 칸은 건너뜀
			}
			if (vo.getSigunguName().isEmpty()) vo.setSigunguName(ApiClient.str(it, "signguNm"));
		}
		vo.setTruncated(totalCount(root) > items.size());
		vo.finish();
		CrowdVO result = vo;
		if (!vo.isAvailable()) {
			result = CrowdVO.unavailable("이 시·군·구는 붐빔 예측 자료가 없어요.");
		}
		synchronized (cache) {
			cache.put(key, new Object[] { result, System.currentTimeMillis() + ApiClient.TTL_CROWD });
		}
		return result;
	}

	private static int totalCount(JsonObject root) {
		try {
			return root.getAsJsonObject("response").getAsJsonObject("body").get("totalCount").getAsInt();
		} catch (Exception e) {
			return 0;
		}
	}
}
