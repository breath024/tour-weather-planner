package com.inha.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

// 남용 방지 (TourAPI 하루 호출 한도를 지키려고):
//  /api/around  세션마다 1분에 20번 · 같은 IP 에서 1분에 30번 · 서버 전체로 1분에 40번
//  POST /course 담기(add · addPlan · import)  세션마다 1분에 20번 (담을 때마다 관광지 확인 호출이 나가서)
//  GET /course?share=… 공유 화면 · 공유 .ics  화면을 여는 횟수가 아니라 「새로 관광공사에 물어봐야 하는 번호」 수를 센다
//     (이미 이 세션이 본 번호 · 캐시에 있는 번호는 0개 → 막지 않는다). 세션마다 1분에 15개 · 서버 전체로 1분에 30개
public class RateLimiter {

	public static final int PER_SESSION = 20;
	public static final int PER_IP = 30;
	public static final int GLOBAL = 40;
	public static final int ADD_PER_SESSION = 20;
	public static final int SHARE_NEW_PER_SESSION = 15;  // 공유 링크의 새 번호(관광지 확인 호출이 나간다) 세션마다 1분에 15개
	public static final int SHARE_NEW_GLOBAL = 30;       // 서버 전체로 1분에 30개 (쿠키 없이 요청해서 세션을 매번 새로 받는 경우도 막는다)
	private static final int IP_MAP_MAX = 2000;   // IP 기록 칸 상한 (넘으면 오래된 기록부터 정리)
	private static final long WINDOW_MS = 60_000L;
	private static final String ATTR = "aroundHits";
	private static final String ATTR_ADD = "addHits";
	private static final String ATTR_SHARE = "shareHits";
	private static final Deque<Long> shareGlobalHits = new ArrayDeque<>();
	private static final Deque<Long> globalHits = new ArrayDeque<>();
	private static final Map<String, Deque<Long>> ipHits = new HashMap<>();

	// /api/around 용. 허용이면 0, 막히면 다시 시도할 수 있을 때까지 남은 초(1 이상)
	public static int tryAcquire(HttpSession session, String ip) {
		long now = System.currentTimeMillis();
		synchronized (globalHits) {
			trim(globalHits, now);
			Deque<Long> mine = hitsOf(session, ATTR, now);
			if (mine.size() >= PER_SESSION) return wait(mine, now);
			Deque<Long> byIp = ipHits.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
			trim(byIp, now);
			if (byIp.size() >= PER_IP) return wait(byIp, now);
			if (globalHits.size() >= GLOBAL) return wait(globalHits, now);
			mine.addLast(now);
			byIp.addLast(now);
			globalHits.addLast(now);
			cleanIpMap(now);
			return 0;
		}
	}

	// POST /course 담기 용 (세션마다 1분에 20번). 허용이면 0, 막히면 남은 초
	public static int tryAcquireAdd(HttpSession session) {
		long now = System.currentTimeMillis();
		synchronized (globalHits) {
			Deque<Long> mine = hitsOf(session, ATTR_ADD, now);
			if (mine.size() >= ADD_PER_SESSION) return wait(mine, now);
			mine.addLast(now);
			return 0;
		}
	}

	// 공유 링크 화면 · 공유 링크의 .ics 용. 남이 보낸 번호를 관광공사에 확인하러 가는 호출이 문제라서,
	// 화면을 연 횟수가 아니라 「새로 확인해야 하는 번호 수(newIds)」를 센다. 0개면 호출이 없으니 항상 허용.
	// 허용이면 0, 막히면 남은 초 (막힐 땐 아무것도 세지 않는다)
	public static int tryAcquireShare(HttpSession session, int newIds) {
		if (newIds <= 0) return 0;
		long now = System.currentTimeMillis();
		synchronized (globalHits) {
			trim(shareGlobalHits, now);
			Deque<Long> mine = hitsOf(session, ATTR_SHARE, now);
			if (mine.size() + newIds > SHARE_NEW_PER_SESSION) return wait(mine.isEmpty() ? shareGlobalHits : mine, now);
			if (shareGlobalHits.size() + newIds > SHARE_NEW_GLOBAL) return wait(shareGlobalHits, now);
			for (int i = 0; i < newIds; i++) {
				mine.addLast(now);
				shareGlobalHits.addLast(now);
			}
			return 0;
		}
	}

	// 세션에 저장된 호출 기록 (없으면 만든다). 세션에 저장되므로 Serializable 인 LinkedList
	@SuppressWarnings("unchecked")
	private static Deque<Long> hitsOf(HttpSession session, String attr, long now) {
		Deque<Long> mine = (Deque<Long>) session.getAttribute(attr);
		if (mine == null) {
			mine = new LinkedList<>();
			session.setAttribute(attr, mine);
		}
		trim(mine, now);
		return mine;
	}

	private static void trim(Deque<Long> q, long now) {
		while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst();
	}

	// IP 기록이 너무 많아지면 1분 넘게 조용한 IP 를 지운다 (메모리가 무한히 늘지 않게)
	private static void cleanIpMap(long now) {
		if (ipHits.size() <= IP_MAP_MAX) return;
		ipHits.values().removeIf(q -> {
			trim(q, now);
			return q.isEmpty();
		});
		if (ipHits.size() > IP_MAP_MAX) ipHits.clear();
	}

	private static int wait(Deque<Long> q, long now) {
		if (q.isEmpty()) return 60;   // 한 번에 너무 많이 물어본 경우 (기록이 없어도 1분은 기다리게)
		long left = WINDOW_MS - (now - q.peekFirst());
		return (int) Math.max(1, (left + 999) / 1000);
	}
}
