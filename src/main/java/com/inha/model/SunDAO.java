package com.inha.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// API ⑥ 한국천문연구원 출몰시각 (RiseSetInfoService / getLCRiseSetInfo) — 위치 · 날짜별 해 뜨는 · 지는 시각
// [확인 2026-11-18] 경도 · 위도는 "소수 도(degree)" 로 넣는다 (예: longitude=129.2 latitude=35.8).
// 명세에 있던 "×100 정수"(12922 · 3584) 는 틀렸다: 그렇게 넣으면 위치를 못 읽고 늘 독도(131.87, 37.23) 값을 돌려준다.
// 소수 도를 넣으면 가장 가까운 지점 이름(location: 경주 · 제주 · 광명 …)과 그 값이 온다.
// 캐시는 날짜 + 좌표(소수 1자리, 약 9km → 해 뜨는 시각 차이는 1분 안쪽) 단위 24시간.
public class SunDAO {

	private static final String URL = "https://apis.data.go.kr/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
	private static final String API_NAME = "한국천문연구원 출몰시각";
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final String serviceKey;

	public SunDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	// 좌표(위도 · 경도)와 날짜로 해 뜨는 · 지는 시각을 가져온다
	public SunVO times(double lat, double lon, LocalDate date) throws ApiException {
		String url = URL + "?serviceKey=" + serviceKey
				+ "&locdate=" + date.format(YMD)
				+ "&longitude=" + String.format(Locale.ROOT, "%.1f", lon)
				+ "&latitude=" + String.format(Locale.ROOT, "%.1f", lat)
				+ "&dnYn=Y&_type=json";
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_SUN);
		String code = ApiClient.resultCode(root);
		if (!code.equals("00")) {
			throw new ApiException(API_NAME + " 오류: " + ApiClient.resultMsg(root) + " (" + code + ")");
		}
		JsonArray items = ApiClient.items(root); // 1건이면 객체로 와도 ApiClient.items 가 배열로 바꿔 줌
		if (items.size() == 0) {
			throw new ApiException("해 뜨는 · 지는 시각 자료가 없어요.");
		}
		JsonObject it = items.get(0).getAsJsonObject();
		SunVO vo = new SunVO();
		vo.setDate(date);
		vo.setLocation(ApiClient.str(it, "location"));
		vo.setSunrise(SunVO.clock(ApiClient.str(it, "sunrise")));
		vo.setSunset(SunVO.clock(ApiClient.str(it, "sunset")));
		vo.setCivilDawn(SunVO.clock(ApiClient.str(it, "civilm")));
		vo.setCivilDusk(SunVO.clock(ApiClient.str(it, "civile")));
		return vo;
	}
}
