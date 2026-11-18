package com.inha.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 코스 공유 링크 /course?share=id,id,…&day=N 의 번호 읽기
// 남이 보낸 값이므로 서버가 다시 거른다: 숫자(1~12자리)만 · 중복 제거 · 최대 10곳 · 전체 글자 수 상한
public class ShareLink {

	public static final int MAX_RAW = 200;   // share 값 전체 글자 수 상한 (넘으면 잘라서 읽는다)

	private final List<String> ids = new ArrayList<>();   // 올바른 번호 (순서 유지, 중복 없음)
	private int bad;          // 숫자가 아니라서 버린 칸 수
	private int dup;          // 중복이라 버린 칸 수
	private int over;         // 10곳을 넘어서 버린 칸 수
	private boolean cut;      // 글자 수 상한으로 잘렸나

	// raw: 주소에서 받은 share 값 (null 이면 빈 결과)
	public static ShareLink parse(String raw) {
		ShareLink r = new ShareLink();
		if (raw == null) return r;
		if (raw.length() > MAX_RAW) {
			// 200자에서 자르면 마지막 번호가 가운데서 잘려 다른 번호가 될 수 있다 → 쉼표 앞까지만 남긴다
			int comma = raw.lastIndexOf(',', MAX_RAW);
			raw = comma > 0 ? raw.substring(0, comma) : "";
			r.cut = true;
		}
		Set<String> seen = new LinkedHashSet<>();
		for (String part : raw.split(",", -1)) {
			String s = part.trim();
			if (s.isEmpty()) continue;
			if (!s.matches("\\d{1,12}") || s.matches("0+")) { r.bad++; continue; }
			s = s.replaceFirst("^0+", "");   // 0987844 와 987844 는 같은 번호 (앞의 0 으로 캐시를 피해 가지 못하게)
			if (!seen.add(s)) { r.dup++; continue; }
			if (r.ids.size() >= CourseStore.MAX) { r.over++; continue; }
			r.ids.add(s);
		}
		return r;
	}

	// 공유 링크 주소 (상대 주소): course?share=a,b,c&day=N&date=yyyyMMdd. ids 는 이미 숫자만이라 따로 인코딩하지 않는다
	// day 는 「오늘부터 N일 뒤」라서 받는 사람이 다른 날 열면 날짜가 밀린다 → 보낸 사람이 고른 날짜(date)도 같이 담는다.
	// date 가 없는 옛 링크는 day 만 쓴다
	public static String url(List<String> ids, int day) {
		return "course?share=" + String.join(",", ids) + "&day=" + day + "&date=" + Area.dayOf(day).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
	}

	// date=yyyyMMdd 읽기: 올바른 날짜면 LocalDate, 아니면 null
	public static java.time.LocalDate parseDate(String raw) {
		if (raw == null || !raw.matches("\\d{8}")) return null;
		try {
			return java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
		} catch (java.time.DateTimeException e) {
			return null;
		}
	}

	// 읽다가 버린 것이 있으면 화면에 알릴 문구 (없으면 "")
	public String notice() {
		List<String> parts = new ArrayList<>();
		if (bad > 0) parts.add("번호가 아닌 값 " + bad + "개");
		if (dup > 0) parts.add("겹치는 번호 " + dup + "개");
		if (over > 0) parts.add(CourseStore.MAX + "곳을 넘은 " + over + "곳");
		if (cut) parts.add("너무 길어서 잘린 뒷부분");
		if (parts.isEmpty()) return "";
		return "링크에 잘못된 값이 있어서 빼고 열었어요 (" + String.join(" · ", parts) + ").";
	}

	public List<String> getIds() { return ids; }
	public boolean isEmpty() { return ids.isEmpty(); }
	public int getBad() { return bad; }
	public int getDup() { return dup; }
	public int getOver() { return over; }
	public boolean isCut() { return cut; }
}
