package com.inha.model;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;

// 내 코스(HttpSession 에 담는 관광지 목록) 다루기 + CSRF 토큰 + 한 번만 보이는 알림
public class CourseStore {

	public static final int MAX = 10;                    // 한 코스에 담을 수 있는 최대 곳 수
	private static final String COURSE = "course";
	private static final String TOKEN = "csrfToken";
	private static final String FLASH = "flashMsg";
	private static final String FLASH_OK = "flashOk";
	private static final Object LOCK = new Object();
	private static final SecureRandom RANDOM = new SecureRandom();

	public static final String ADDED = "ADDED";
	public static final String DUPLICATE = "DUPLICATE";
	public static final String FULL = "FULL";

	// 세션의 코스 목록 (없으면 만든다). 같은 세션에서 동시에 요청이 와도 안전하게 이 목록 자체로 잠근다
	@SuppressWarnings("unchecked")
	private static List<CourseItem> raw(HttpSession s) {
		synchronized (LOCK) {
			List<CourseItem> list = (List<CourseItem>) s.getAttribute(COURSE);
			if (list == null) {
				list = new ArrayList<>();
				s.setAttribute(COURSE, list);
			}
			return list;
		}
	}

	// 화면용 복사본
	public static List<CourseItem> list(HttpSession s) {
		List<CourseItem> raw = raw(s);
		synchronized (raw) {
			return new ArrayList<>(raw);
		}
	}

	public static int count(HttpSession s) {
		List<CourseItem> raw = raw(s);
		synchronized (raw) {
			return raw.size();
		}
	}

	public static Set<String> ids(HttpSession s) {
		Set<String> ids = new LinkedHashSet<>();
		for (CourseItem c : list(s)) ids.add(c.getContentId());
		return ids;
	}

	// 담기: 최대 10곳 · 같은 곳은 한 번만
	public static String add(HttpSession s, CourseItem item) {
		List<CourseItem> raw = raw(s);
		synchronized (raw) {
			for (CourseItem c : raw) {
				if (c.getContentId().equals(item.getContentId())) return DUPLICATE;
			}
			if (raw.size() >= MAX) return FULL;
			raw.add(item);
			return ADDED;
		}
	}

	public static boolean remove(HttpSession s, String contentId) {
		List<CourseItem> raw = raw(s);
		synchronized (raw) {
			return raw.removeIf(c -> c.getContentId().equals(contentId));
		}
	}

	// 순서 바꾸기: delta -1 = 위로, +1 = 아래로. 바꿨으면 true
	public static boolean move(HttpSession s, String contentId, int delta) {
		List<CourseItem> raw = raw(s);
		synchronized (raw) {
			for (int i = 0; i < raw.size(); i++) {
				if (raw.get(i).getContentId().equals(contentId)) {
					int j = i + delta;
					if (j < 0 || j >= raw.size()) return false;
					CourseItem tmp = raw.get(i);
					raw.set(i, raw.get(j));
					raw.set(j, tmp);
					return true;
				}
			}
			return false;
		}
	}

	public static void clear(HttpSession s) {
		List<CourseItem> raw = raw(s);
		synchronized (raw) {
			raw.clear();
		}
	}

	// ---- 화면에 보여 준 하루 코스의 번호들: addPlan 은 이 번호만 받는다 (아무 번호나 보내 API 를 마구 부르지 못하게) ----
	private static final String PLAN_IDS = "planIds";
	private static final int PLAN_IDS_MAX = 60;

	public static void rememberPlanIds(HttpSession s, String idsCsv) {
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Set<String> set = (Set<String>) s.getAttribute(PLAN_IDS);
			if (set == null || set.size() > PLAN_IDS_MAX) set = new LinkedHashSet<>();
			for (String id : idsCsv.split(",")) {
				if (id.matches("\\d{1,12}")) set.add(id);
			}
			s.setAttribute(PLAN_IDS, set);
		}
	}

	public static boolean isShownPlanId(HttpSession s, String id) {
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Set<String> set = (Set<String>) s.getAttribute(PLAN_IDS);
			return set != null && set.contains(id);
		}
	}

	// 하루 코스 칸의 시간("10:00–12:00")도 같이 기억해 둔다: 담을 때 CourseItem 에 붙여 달력(.ics) 시간에 쓴다
	private static final String PLAN_SLOTS = "planSlots";

	public static void rememberPlanSlots(HttpSession s, DayPlanVO plan) {
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) s.getAttribute(PLAN_SLOTS);
			if (map == null || map.size() > PLAN_IDS_MAX) map = new LinkedHashMap<>();
			for (PlanBlockVO b : plan.getBlocks()) {
				if (b.getSpot() != null && b.getTimeLabel() != null) map.put(b.getSpot().getContentId(), b.getTimeLabel());
			}
			s.setAttribute(PLAN_SLOTS, map);
		}
	}

	// 하루 코스로 보여 준 칸의 시간표 문구 (없으면 null)
	public static String planSlotLabel(HttpSession s, String id) {
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) s.getAttribute(PLAN_SLOTS);
			return map == null ? null : map.get(id);
		}
	}

	// ---- 행사 기간: 행사 목록 · 상세에서 본 기간을 기억해 두었다가 담을 때 CourseItem 에 붙인다 (담으려고 또 부르지 않는다) ----
	private static final String EVENT_PERIODS = "eventPeriods";

	public static void rememberEventPeriod(HttpSession s, String id, String start, String end) {
		if (id == null || !id.matches("\\d{1,12}") || start == null || !start.matches("\\d{8}")) return;
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) s.getAttribute(EVENT_PERIODS);
			if (map == null || map.size() > PLAN_IDS_MAX) map = new LinkedHashMap<>();
			map.put(id, start + "," + (end == null ? "" : end));
			s.setAttribute(EVENT_PERIODS, map);
		}
	}

	// 기억해 둔 기간 {시작, 끝} (없으면 null)
	public static String[] eventPeriodOf(HttpSession s, String id) {
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) s.getAttribute(EVENT_PERIODS);
			String v = map == null ? null : map.get(id);
			return v == null ? null : v.split(",", -1);
		}
	}

	// ---- 공유 링크: 공유 화면(/course?share=…)에서 보여 준 번호만 「내 코스로 가져오기」가 받는다 ----
	private static final String SHARE_IDS = "shareIds";

	public static void rememberShareIds(HttpSession s, List<String> ids) {
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Set<String> set = (Set<String>) s.getAttribute(SHARE_IDS);
			if (set == null || set.size() > PLAN_IDS_MAX) set = new LinkedHashSet<>();
			set.addAll(ids);
			s.setAttribute(SHARE_IDS, set);
		}
	}

	public static boolean isShownShareId(HttpSession s, String id) {
		synchronized (LOCK) {
			@SuppressWarnings("unchecked")
			Set<String> set = (Set<String>) s.getAttribute(SHARE_IDS);
			return set != null && set.contains(id);
		}
	}

	// ---- CSRF: 세션마다 무작위 토큰 하나, POST 때 같은지 확인 ----
	public static String token(HttpSession s) {
		synchronized (LOCK) {
			String t = (String) s.getAttribute(TOKEN);
			if (t == null) {
				byte[] b = new byte[16];
				RANDOM.nextBytes(b);
				StringBuilder sb = new StringBuilder();
				for (byte x : b) sb.append(String.format("%02x", x));
				t = sb.toString();
				s.setAttribute(TOKEN, t);
			}
			return t;
		}
	}

	public static boolean checkToken(HttpSession s, String given) {
		if (given == null) return false;
		String expected = token(s);
		// 시간차 공격을 막으려고 길이와 상관없이 끝까지 비교하는 방식 사용
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
	}

	// ---- 알림: 처리 결과를 세션에 두었다가 다음 화면에서 한 번만 보여 준다 (PRG) ----
	public static void flash(HttpSession s, String message, boolean ok) {
		s.setAttribute(FLASH, message);
		s.setAttribute(FLASH_OK, ok);
	}

	// 알림을 꺼내고 지운다. 없으면 null
	public static String[] takeFlash(HttpSession s) {
		String msg = (String) s.getAttribute(FLASH);
		if (msg == null) return null;
		Object ok = s.getAttribute(FLASH_OK);
		s.removeAttribute(FLASH);
		s.removeAttribute(FLASH_OK);
		return new String[] { msg, String.valueOf(Boolean.TRUE.equals(ok)) };
	}
}
