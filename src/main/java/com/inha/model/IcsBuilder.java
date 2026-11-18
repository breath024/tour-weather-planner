package com.inha.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 내 코스 → 캘린더 파일(.ics, RFC 5545) 만들기
//  - 줄바꿈은 CRLF, 한 줄은 75바이트(UTF-8 기준)까지. 넘으면 CRLF + 공백 한 칸으로 이어 쓴다(줄 접기).
//    한글(3바이트) · 이모지(4바이트) 글자는 중간에 끊지 않고, `\n` 같은 이스케이프 쌍도 가운데서 끊지 않는다.
//  - 글 값(SUMMARY · LOCATION · DESCRIPTION)은 \ ; , 줄바꿈을 이스케이프한다.
//  - 시간은 TZID=Asia/Seoul + VTIMEZONE (한국은 서머타임이 없어서 +09:00 하나)
//  - 시간 정하기: 코스 순서대로 10:00 부터 2시간씩 (8곳 이상이면 하루 안에 들어가게 1시간씩).
//    하루 코스에서 담은 곳은 그 칸 시간을 쓰되, 순서를 바꿔서 앞 곳의 끝보다 이르면 칸 시간을 버리고 앞 곳 바로 뒤에 둔다
//    (시작 시각이 순서대로 늘어나고 서로 겹치지 않게)
public class IcsBuilder {

	public static final String CRLF = "\r\n";
	private static final int MAX_OCTETS = 75;                  // 줄 하나(CRLF 제외)의 최대 바이트
	private static final int START_MIN = 10 * 60;              // 시간이 정해지지 않은 곳은 10:00 부터
	private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

	// items: 코스 순서대로, wx: contentId → 그날 날씨 한 줄(없는 곳은 비어 있어도 됨),
	// date: 달력에 넣을 날짜, dayLabel: "내일 11/19(목)", day: 오늘 기준 며칠 뒤(상세 링크용),
	// baseUrl: "http://host:8095/TourWeather/" 처럼 /로 끝나는 앱 주소(모르면 "" → 상세 링크 줄을 뺀다), now: DTSTAMP 시각
	public static String build(List<CourseItem> items, Map<String, String> wx, LocalDate date, String dayLabel, int day,
			String baseUrl, Instant now) {
		List<String> lines = new ArrayList<>();
		lines.add("BEGIN:VCALENDAR");
		lines.add("VERSION:2.0");
		lines.add("PRODID:-//TourWeather//Course//KO");
		lines.add("CALSCALE:GREGORIAN");
		lines.add("METHOD:PUBLISH");
		lines.add("X-WR-CALNAME:" + escapeText("내 여행 코스 " + dayLabel));
		lines.add("X-WR-TIMEZONE:Asia/Seoul");
		// 한국 표준시 (서머타임 없음)
		lines.add("BEGIN:VTIMEZONE");
		lines.add("TZID:Asia/Seoul");
		lines.add("BEGIN:STANDARD");
		lines.add("DTSTART:19700101T000000");
		lines.add("TZOFFSETFROM:+0900");
		lines.add("TZOFFSETTO:+0900");
		lines.add("TZNAME:KST");
		lines.add("END:STANDARD");
		lines.add("END:VTIMEZONE");

		int[][] times = times(items);
		String stamp = UTC.format(now);
		String ymd = date.format(DateTimeFormatter.BASIC_ISO_DATE);
		for (int i = 0; i < items.size(); i++) {
			CourseItem it = items.get(i);
			LocalDateTime start = date.atStartOfDay().plusMinutes(times[i][0]);
			LocalDateTime end = date.atStartOfDay().plusMinutes(times[i][1]);
			lines.add("BEGIN:VEVENT");
			lines.add("UID:tw-" + ymd + "-" + (i + 1) + "-" + it.getContentId() + "@tourweather");
			lines.add("DTSTAMP:" + stamp);
			lines.add("DTSTART;TZID=Asia/Seoul:" + LOCAL.format(start));
			lines.add("DTEND;TZID=Asia/Seoul:" + LOCAL.format(end));
			lines.add("SUMMARY:" + escapeText(it.getTitle()));
			if (it.getAddr() != null && !it.getAddr().trim().isEmpty()) lines.add("LOCATION:" + escapeText(it.getAddr()));
			lines.add("DESCRIPTION:" + escapeText(description(it, wx == null ? null : wx.get(it.getContentId()), day, baseUrl, times[i][2] == 1, date)));
			if (it.isHasLocation()) lines.add("GEO:" + it.getLat() + ";" + it.getLon());   // 값 사이 ; 는 GEO 의 구분자라 이스케이프하지 않는다
			if (baseUrl != null && !baseUrl.isEmpty()) lines.add("URL:" + detailUrl(baseUrl, it, day));
			lines.add("STATUS:CONFIRMED");
			lines.add("END:VEVENT");
		}
		lines.add("END:VCALENDAR");

		StringBuilder out = new StringBuilder();
		for (String l : lines) fold(out, l);
		return out.toString();
	}

	// 곳마다 {시작 분, 끝 분, 칸 시간을 썼나(1/0)} (하루 시작 기준). 24시를 넘으면 다음 날로 넘어가므로 24:00 에서 자른다
	public static int[][] times(List<CourseItem> items) {
		int n = items.size();
		int each = n <= 7 ? 120 : 60;
		int[][] t = withSlots(items, each);
		if (t != null) return t;
		// 칸 시간을 따르면 24:00 을 넘는 경우(예: 저녁 칸을 맨 위로 옮김) → 칸 시간을 모두 버리고
		// 10:00 부터 순서대로 다시 잡는다. 그래도 모자라면 한 곳 시간을 줄여서 겹치지 않게 한다.
		int step = Math.max(15, Math.min(each, (24 * 60 - START_MIN) / Math.max(1, n)));
		t = new int[n][3];
		int cursor = START_MIN;
		for (int i = 0; i < n; i++) {
			int s = Math.min(cursor, 24 * 60 - 1);
			int e = Math.min(s + step, 24 * 60);
			t[i][0] = s;
			t[i][1] = e;
			cursor = e;
		}
		return t;
	}

	// 칸 시간을 따르며 순서대로 놓는다. 앞 칸보다 이른 칸 시간은 버린다. 24:00 을 넘으면 null
	private static int[][] withSlots(List<CourseItem> items, int each) {
		int n = items.size();
		int cursor = START_MIN;
		int[][] t = new int[n][3];   // {시작 분, 끝 분, 칸 시간을 썼으면 1}
		for (int i = 0; i < n; i++) {
			CourseItem it = items.get(i);
			int s, e;
			if (it.isHasSlot() && it.getSlotStartMin() >= cursor) {
				s = it.getSlotStartMin();
				e = it.getSlotEndMin();
				t[i][2] = 1;   // 칸 시간을 그대로 썼다
			} else {
				s = cursor;
				e = cursor + each;
			}
			if (e > 24 * 60) return null;
			t[i][0] = s;
			t[i][1] = e;
			cursor = e;
		}
		return t;
	}

	private static String detailUrl(String baseUrl, CourseItem it, int day) {
		return baseUrl + "detail?id=" + it.getContentId() + "&type=" + it.getContentTypeId() + "&day=" + day;
	}

	// 일정 메모: 종류 · 날씨 · 주소 · 상세 링크
	private static String description(CourseItem it, String weatherLine, int day, String baseUrl, boolean fromSlot, LocalDate date) {
		StringBuilder d = new StringBuilder();
		String place = it.isPlaceUnknown() ? "실내 · 야외 미확인" : it.isIndoor() ? "실내" : "실외";
		d.append(it.getTypeName() == null ? "" : it.getTypeName()).append(" · ").append(place).append('\n');
		String evNotice = it.eventNotice(date);   // 행사가 이 날 안 열리면 먼저 알린다
		if (!evNotice.isEmpty()) d.append("주의: ").append(evNotice).append('\n');
		d.append("날씨: ").append(weatherLine == null || weatherLine.isEmpty() ? "불러오지 못했어요" : weatherLine).append('\n');
		if (it.getAddr() != null && !it.getAddr().trim().isEmpty()) d.append("주소: ").append(it.getAddr()).append('\n');
		if (baseUrl != null && !baseUrl.isEmpty()) d.append("상세 보기: ").append(detailUrl(baseUrl, it, day)).append('\n');
		if (!fromSlot) d.append("시간은 코스 순서대로 임시로 잡았어요. 일정에 맞게 바꿔 쓰세요.");
		return d.toString().trim();
	}

	// 글 값 이스케이프 (RFC 5545 3.3.11): \ → \\, ; → \;, , → \,, 줄바꿈 → \n. 그 밖의 제어 문자와 짝 잃은 서로게이트는 뺀다
	public static String escapeText(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder();
		int[] cps = s.codePoints().toArray();
		for (int i = 0; i < cps.length; i++) {
			int c = cps[i];
			if (c == '\\') sb.append("\\\\");
			else if (c == ';') sb.append("\\;");
			else if (c == ',') sb.append("\\,");
			else if (c == '\n') sb.append("\\n");
			else if (c == '\r') {
				if (!(i + 1 < cps.length && cps[i + 1] == '\n')) sb.append("\\n"); // \r\n 은 \n 에서 한 번만, 혼자인 \r 도 줄바꿈으로
			} else if (c == '\t') sb.append(' ');
			else if (c < 0x20 || c == 0x7f) continue;
			else if (c >= 0xD800 && c <= 0xDFFF) continue; // codePoints() 가 짝을 맞춘 뒤에도 남는 서로게이트 = 깨진 글자
			else sb.appendCodePoint(c);
		}
		return sb.toString();
	}

	// 줄 접기 (RFC 5545 3.1): 한 줄 75바이트(CRLF 제외). 이어지는 줄은 맨 앞 공백 한 칸도 75바이트에 든다
	static void fold(StringBuilder out, String line) {
		int used = 0;
		int i = 0;
		while (i < line.length()) {
			int cp = line.codePointAt(i);
			int len = Character.charCount(cp);
			// 이스케이프 쌍(\n \, \; \\)은 한 덩어리로 다뤄 가운데서 끊지 않는다
			if (cp == '\\' && i + 1 < line.length()) {
				int next = line.codePointAt(i + 1);
				len += Character.charCount(next);
			}
			int bytes = line.substring(i, i + len).getBytes(StandardCharsets.UTF_8).length;
			if (used + bytes > MAX_OCTETS) {
				out.append(CRLF).append(' ');
				used = 1;
			}
			out.append(line, i, i + len);
			used += bytes;
			i += len;
		}
		out.append(CRLF);
	}
}
