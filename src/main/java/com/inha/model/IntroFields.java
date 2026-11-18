package com.inha.model;

import java.util.HashMap;
import java.util.Map;

// detailIntro2 는 관광 타입마다 칸 이름이 다르다 → 타입별로 {API 칸 이름, 화면에 보일 이름} 짝을 모아 둔 표
public class IntroFields {

	private static final Map<String, String[][]> TABLE = new HashMap<>();
	static {
		// 12 관광지
		TABLE.put("12", new String[][] {
			{ "usetime", "이용시간" }, { "restdate", "쉬는 날" }, { "useseason", "이용 시기" },
			{ "parking", "주차" }, { "infocenter", "문의" }, { "expguide", "체험 안내" },
			{ "expagerange", "체험 가능 연령" }, { "chkbabycarriage", "유모차 대여" }, { "chkpet", "반려동물" },
			{ "chkcreditcard", "신용카드" }, { "accomcount", "수용 인원" }, { "opendate", "개장일" } });
		// 14 문화시설
		TABLE.put("14", new String[][] {
			{ "usetimeculture", "이용시간" }, { "restdateculture", "쉬는 날" }, { "usefee", "이용 요금" },
			{ "spendtime", "소요 시간" }, { "parkingculture", "주차" }, { "parkingfee", "주차 요금" },
			{ "infocenterculture", "문의" }, { "discountinfo", "할인 정보" }, { "scale", "규모" },
			{ "accomcountculture", "수용 인원" }, { "chkbabycarriageculture", "유모차 대여" },
			{ "chkpetculture", "반려동물" }, { "chkcreditcardculture", "신용카드" } });
		// 15 축제 · 공연 · 행사
		TABLE.put("15", new String[][] {
			{ "eventstartdate", "시작일" }, { "eventenddate", "종료일" }, { "eventplace", "장소" },
			{ "playtime", "공연 시간" }, { "usetimefestival", "이용 요금" }, { "agelimit", "관람 연령" },
			{ "spendtimefestival", "소요 시간" }, { "sponsor1", "주최" }, { "sponsor1tel", "주최 문의" },
			{ "sponsor2", "주관" }, { "sponsor2tel", "주관 문의" }, { "program", "프로그램" } });
		// 25 여행코스
		TABLE.put("25", new String[][] {
			{ "distance", "코스 거리" }, { "taketime", "소요 시간" }, { "schedule", "코스 일정" },
			{ "theme", "코스 테마" }, { "infocentertourcourse", "문의" } });
		// 28 레포츠
		TABLE.put("28", new String[][] {
			{ "usetimeleports", "이용시간" }, { "restdateleports", "쉬는 날" }, { "openperiod", "개장 기간" },
			{ "usefeeleports", "이용 요금" }, { "parkingleports", "주차" }, { "parkingfeeleports", "주차 요금" },
			{ "infocenterleports", "문의" }, { "reservation", "예약 안내" }, { "scaleleports", "규모" },
			{ "expagerangeleports", "체험 가능 연령" }, { "chkbabycarriageleports", "유모차 대여" },
			{ "chkpetleports", "반려동물" }, { "chkcreditcardleports", "신용카드" } });
		// 32 숙박
		TABLE.put("32", new String[][] {
			{ "checkintime", "입실 시간" }, { "checkouttime", "퇴실 시간" }, { "roomcount", "객실 수" },
			{ "roomtype", "객실 유형" }, { "parkinglodging", "주차" }, { "infocenterlodging", "문의" },
			{ "reservationlodging", "예약 안내" }, { "reservationurl", "예약 주소" }, { "chkcooking", "취사" },
			{ "pickup", "픽업" }, { "foodplace", "식음료장" }, { "subfacility", "부대시설" } });
		// 38 쇼핑
		TABLE.put("38", new String[][] {
			{ "opentime", "영업시간" }, { "restdateshopping", "쉬는 날" }, { "saleitem", "판매 품목" },
			{ "shopguide", "매장 안내" }, { "parkingshopping", "주차" }, { "infocentershopping", "문의" },
			{ "scaleshopping", "규모" }, { "fairday", "장 서는 날" }, { "chkpetshopping", "반려동물" },
			{ "chkcreditcardshopping", "신용카드" } });
		// 39 음식점
		TABLE.put("39", new String[][] {
			{ "opentimefood", "영업시간" }, { "restdatefood", "쉬는 날" }, { "firstmenu", "대표 메뉴" },
			{ "treatmenu", "취급 메뉴" }, { "parkingfood", "주차" }, { "infocenterfood", "문의" },
			{ "reservationfood", "예약 안내" }, { "packing", "포장" }, { "seat", "좌석 수" },
			{ "discountinfofood", "할인 정보" }, { "chkcreditcardfood", "신용카드" } });
	}

	public static String[][] of(String contentTypeId) {
		String[][] fields = TABLE.get(contentTypeId);
		return fields == null ? new String[0][] : fields;
	}
}
