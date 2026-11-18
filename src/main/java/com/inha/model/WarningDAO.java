package com.inha.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// API ⑧ 기상청 기상특보 (WthrWrnInfoService / getPwnStatus) — 지금 발효 중인 특보 현황 문장
// 응답은 문장 덩어리다 (2026-11-18 실측):
//   t6 (현재 특보): "o 강풍주의보 : 서해5도, 충청남도(태안, 당진, 보령(도서제외)), 전라남도(여수, …), 울릉도.독도\r\no 한파주의보 : 강원도(태백, …), 경상북도(…)"
//   t7 (예비특보): "(1) 강풍 예비특보\r\no 11월 18일 오후(12시~18시) : 강원도(태백, …)\r\n(2) 풍랑 예비특보\r\no … : 남해서부동쪽먼바다"
// → 줄마다 "종류 : 지역들" 로 나누고, 지역들은 괄호 밖의 쉼표로 잘라 시도 이름 + 괄호 안 세부 지역으로 가른다.
// 바다 구역(먼바다 · 앞바다)은 시도 이름과 안 맞으니 자연히 걸러진다. 특보가 없으면 t6 가 비어 목록이 비어 있다.
public class WarningDAO {

	private static final String URL = "https://apis.data.go.kr/1360000/WthrWrnInfoService/getPwnStatus";
	private static final String API_NAME = "기상청 기상특보";

	private final String serviceKey;

	public WarningDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	// 전국 특보 현황 (10분 캐시). 시도별로는 WarningVO.forSido(RegionCodes.warnNames(sido))
	public WarningVO status() throws ApiException {
		String url = URL + "?serviceKey=" + serviceKey + "&dataType=JSON&numOfRows=10&pageNo=1";
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_WARN);
		String code = ApiClient.resultCode(root);
		WarningVO vo = new WarningVO();
		if (code.equals("03")) return vo; // 자료 없음 = 특보 없음
		if (!code.equals("00")) {
			throw new ApiException(API_NAME + " 오류: " + ApiClient.resultMsg(root) + " (" + code + ")");
		}
		JsonArray items = ApiClient.items(root);
		if (items.size() == 0) return vo;
		JsonObject it = items.get(0).getAsJsonObject();
		vo.setTmFc(ApiClient.str(it, "tmFc"));
		vo.setTmEf(ApiClient.str(it, "tmEf"));
		parseCurrent(ApiClient.str(it, "t6"), vo.getCurrent());
		parsePrelim(ApiClient.str(it, "t7"), vo.getPrelim());
		return vo;
	}

	// 현재 특보: "o 강풍주의보 : 지역들" 줄들
	static void parseCurrent(String t6, List<WarningItem> out) {
		for (String line : t6.split("\\r?\\n")) {
			String[] kv = splitLine(line);
			if (kv == null) continue;
			addRegions(out, kv[0], false, "", kv[1]);
		}
	}

	// 예비특보: "(1) 강풍 예비특보" 머리글 아래의 "o 11월 18일 오후(12시~18시) : 지역들" 줄들
	static void parsePrelim(String t7, List<WarningItem> out) {
		String kind = "예비특보";
		for (String line : t7.split("\\r?\\n")) {
			String s = line.trim();
			if (s.matches("^\\(\\d+\\)\\s*.+")) { // 머리글
				kind = s.replaceFirst("^\\(\\d+\\)\\s*", "");
				continue;
			}
			String[] kv = splitLine(s);
			if (kv == null) continue;
			addRegions(out, kind, true, kv[0], kv[1]);
		}
	}

	// "11월 18일 오후(12시~18시)" → 올해 11/18 (못 읽으면 null). 연말에 "1월 2일" 이 나오면 내년으로 본다
	static LocalDate parseDate(String when) {
		Matcher m = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일").matcher(when == null ? "" : when);
		if (!m.find()) return null;
		try {
			LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
			LocalDate d = LocalDate.of(today.getYear(), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
			return d.isBefore(today.minusMonths(6)) ? d.plusYears(1) : d;
		} catch (Exception e) { // 없는 날짜
			return null;
		}
	}

	// "o 종류 : 지역들" → { 종류, 지역들 } (그 모양이 아니면 null)
	private static String[] splitLine(String line) {
		String s = line.trim();
		if (!s.startsWith("o ") && !s.startsWith("O ")) return null;
		int c = s.indexOf(" : ");
		if (c < 0) return null;
		return new String[] { s.substring(2, c).trim(), s.substring(c + 3).trim() };
	}

	// "충청남도(태안, 보령(도서제외)), 전라남도(여수), 울릉도.독도" 를 괄호 밖 쉼표로 잘라 한 칸씩 만든다
	private static void addRegions(List<WarningItem> out, String kind, boolean prelim, String when, String regions) {
		List<String> parts = WarningItem.splitTop(regions);
		LocalDate date = prelim ? parseDate(when) : null;

		for (String p : parts) {
			p = p.trim();
			if (p.isEmpty()) continue;
			WarningItem w = new WarningItem();
			w.setKind(kind);
			w.setPrelim(prelim);
			w.setWhen(when);
			w.setDate(date);
			int open = p.indexOf('(');
			if (open < 0) {
				w.setSido(p);
			} else {
				w.setSido(p.substring(0, open).trim());
				int close = p.lastIndexOf(')');
				w.setDetail(close > open ? p.substring(open + 1, close).trim() : p.substring(open + 1).trim());
			}
			out.add(w);
		}
	}
}
