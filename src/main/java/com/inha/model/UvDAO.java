package com.inha.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// API ⑦ 기상청 생활기상지수 V5 중 자외선지수 (LivingWthrIdxServiceV5 / getUVIdxV5)
// areaNo = 시도 + 시군구 + "00000" (RegionCodes.uvAreaNo). 시군구 코드가 없으면(resultCode 99) 시도 단위로 다시 부른다.
// time 은 3시간 단위로 내린 현재 시각(YYYYMMDDHH). [확인 2026-11-18] 17시를 넣어도 15시 칸부터 돌려주므로 3시간 단위로 맞춰 부르면 캐시가 잘 맞는다.
public class UvDAO {

	private static final String URL = "https://apis.data.go.kr/1360000/LivingWthrIdxServiceV5/getUVIdxV5";
	private static final String API_NAME = "기상청 자외선 지수";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHH");
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final String serviceKey;

	public UvDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	// 시도 코드 + 시군구 코드(없으면 "")로 자외선 예보를 가져온다
	public UvVO forecast(String sido, String sigungu) throws ApiException {
		String areaNo = RegionCodes.uvAreaNo(sido, sigungu);
		String sidoAreaNo = RegionCodes.uvSidoAreaNo(sido);
		if (areaNo == null || sidoAreaNo == null) {
			throw new ApiException("이 지역의 자외선 정보는 아직 지원하지 않아요.");
		}
		LocalDateTime now = LocalDateTime.now(KST);
		LocalDateTime slot = now.withHour(now.getHour() / 3 * 3).withMinute(0).withSecond(0).withNano(0);

		// ① 시군구 단위 → ② 시도 단위 → ③ 시도 단위, 3시간 전 칸 (방금 칸이 아직 안 올라왔을 때)
		JsonObject item = fetch(areaNo, slot);
		boolean fallback = false;
		if (item == null && !areaNo.equals(sidoAreaNo)) {
			item = fetch(sidoAreaNo, slot);
			fallback = true;
			areaNo = sidoAreaNo;
		}
		if (item == null) {
			slot = slot.minusHours(3);
			item = fetch(sidoAreaNo, slot);
			areaNo = sidoAreaNo;
		}
		if (item == null) {
			throw new ApiException("자외선 자료가 아직 없어요.");
		}

		UvVO vo = new UvVO();
		vo.setAreaNo(areaNo);
		vo.setSidoFallback(fallback);
		// 오늘 낮 값이 남도록 먼저 "오늘 0시 기준" 값을 깔고(실패해도 무시), 그 위에 가장 최근 기준 값을 덮는다
		LocalDateTime midnight = now.toLocalDate().atStartOfDay();
		if (slot.isAfter(midnight)) {
			try {
				addValues(vo, fetch(areaNo, midnight), midnight);
			} catch (ApiException e) {
				// 오늘 아침 값은 덤이라 실패해도 계속
			}
		}
		// 응답의 date(YYYYMMDDHH)가 h0 의 시각
		LocalDateTime start = slot;
		try {
			start = LocalDateTime.parse(ApiClient.str(item, "date"), DateTimeFormatter.ofPattern("yyyyMMddHH"));
		} catch (Exception e) {
			// 못 읽으면 요청한 칸 시각
		}
		vo.setStart(start);
		addValues(vo, item, start);
		return vo;
	}

	// 응답의 h0 ~ h75 를 base 시각부터 3시간 간격으로 vo 에 넣는다 (item 이 null 이면 아무것도 안 함)
	private static void addValues(UvVO vo, JsonObject item, LocalDateTime base) {
		if (item == null) return;
		for (int i = 0; i < 26; i++) {
			String s = ApiClient.str(item, "h" + (3 * i));
			if (s.isEmpty()) continue;
			try {
				vo.setValue(base.plusHours(3L * i), (int) Math.round(Double.parseDouble(s)));
			} catch (NumberFormatException e) {
				// 숫자가 아니면 빈 칸
			}
		}
	}

	// 한 번 부른다. 자료 없음(resultCode 99 · 03)이면 null, 그 밖의 오류는 ApiException
	private JsonObject fetch(String areaNo, LocalDateTime slot) throws ApiException {
		String url = URL + "?serviceKey=" + serviceKey + "&dataType=JSON&numOfRows=10&pageNo=1&areaNo=" + areaNo
				+ "&time=" + slot.format(TIME);
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_UV);
		String code = ApiClient.resultCode(root);
		if (code.equals("99") || code.equals("03")) return null;
		if (!code.equals("00")) {
			throw new ApiException(API_NAME + " 오류: " + ApiClient.resultMsg(root) + " (" + code + ")");
		}
		JsonArray items = ApiClient.items(root);
		return items.size() == 0 ? null : items.get(0).getAsJsonObject();
	}
}
