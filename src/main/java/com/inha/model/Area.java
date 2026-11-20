package com.inha.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 검색 화면에서 고르는 값들: 시·도(법정동 시도 코드) · 날짜
public class Area {

	// TourAPI(KorService2)는 지역을 법정동 시도 코드(lDongRegnCd)로 받는다
	// 2026-11 법정동코드조회(ldongCode2) 결과 기준: 광주·전남은 12(전남광주통합특별시) 하나, 세종은 36110
	private static final Map<String, String> SIDO = new LinkedHashMap<>();
	static {
		SIDO.put("11", "서울");
		SIDO.put("26", "부산");
		SIDO.put("27", "대구");
		SIDO.put("28", "인천");
		SIDO.put("30", "대전");
		SIDO.put("31", "울산");
		SIDO.put("36110", "세종");
		SIDO.put("41", "경기");
		SIDO.put("51", "강원");
		SIDO.put("43", "충북");
		SIDO.put("44", "충남");
		SIDO.put("52", "전북");
		SIDO.put("12", "광주·전남");
		SIDO.put("47", "경북");
		SIDO.put("48", "경남");
		SIDO.put("50", "제주");
	}

	public static Map<String, String> sidoMap() {
		return SIDO;
	}

	// 기본 자리: 내 위치를 못 받을 때 보여 줄 곳 = 인천 개항장 일대(인천역 · 차이나타운). 시군구는 2026 개편 뒤 제물포구(125)
	public static final double HOME_LAT = 37.4757;
	public static final double HOME_LON = 126.6175;
	public static final String HOME_NAME = "인천 개항장 일대";
	public static final String HOME_AREA = "28";
	public static final String HOME_SIGUNGU = "125";

	public static boolean isValid(String code) {
		return code != null && SIDO.containsKey(code);
	}

	public static String name(String code) {
		return SIDO.getOrDefault(code, "");
	}

	// 날짜는 오늘부터 10일 뒤까지 (0~3일 = 단기예보 시간별, 4~10일 = 중기예보 오전 · 오후)
	public static final int MAX_DAY = 10;

	// 0 오늘, 1 내일, 2 모레, 3 글피, 4~10 "N일뒤" (공백이 없어야 화면에서 앞 낱말만 굵게 뽑을 수 있다)
	public static List<String[]> dayOptions() {
		List<String[]> list = new ArrayList<>();
		String[] names = { "오늘", "내일", "모레", "글피" };
		for (int i = 0; i <= MAX_DAY; i++) {
			String name = i < names.length ? names[i] : i + "일뒤";
			list.add(new String[] { String.valueOf(i), name + " " + dateLabel(i) });
		}
		return list;
	}

	public static LocalDate dayOf(int offset) {
		return LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(offset);
	}

	// 예: 11/17(화)
	public static String dateLabel(int offset) {
		LocalDate d = dayOf(offset);
		DayOfWeek w = d.getDayOfWeek();
		return d.getMonthValue() + "/" + d.getDayOfMonth() + "(" + w.getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
	}

	// 주소의 day 값이 잘못됐으면 화면에 알릴 문구, 괜찮으면(없거나 0~10) "".
	// parseDay 가 잘못된 값을 오늘(0)로 바꾸는데 그걸 조용히 하지 않고 알리려고 만든다. 받은 글자는 화면에 되풀이하지 않는다
	public static String dayProblem(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		try {
			int d = Integer.parseInt(raw);
			if (d >= 0 && d <= MAX_DAY) return "";
		} catch (NumberFormatException e) {
			// 숫자가 아님
		}
		return "날짜 값이 올바르지 않아서 오늘 날짜로 열었어요. 날짜는 오늘부터 " + MAX_DAY + "일 뒤까지 고를 수 있어요.";
	}

	public static int parseDay(String s) {
		try {
			int d = Integer.parseInt(s);
			if (d >= 0 && d <= MAX_DAY) return d;
		} catch (Exception e) {
			// 잘못된 값이면 오늘
		}
		return 0;
	}
}
