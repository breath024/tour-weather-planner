package com.inha.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// 실제 길을 따라 가는 동선: 오픈스트리트맵 길 찾기(OSRM). 운영은 FOSSGIS(routing.openstreetmap.de), 키가 필요 없다.
//  - 이용 조건(about 쪽): 초당 1번 이하 · 긁어 가기 · 대량 사용 금지 · 올바른 User-Agent · 출처와 「지도 고치기」 링크 표시
//    → 결과를 24시간 기억하고, 서버 전체에서 요청 사이를 1.1초 넘게 띄운다. 실패하면 null → 화면은 직선 + 어림 그대로
//  - 먼저 도보로 한 번에 묻고, 걸어서 1.5km 넘는 구간이 있으면 차로 한 번 더 물어 그 구간만 바꾼다 (한 동선에 많아야 2번)
//  - 사용자 위치는 보내지 않는다: 부르는 쪽이 관광지 좌표만 넘긴다
public class RoadRoute {
	static final String BASE = "https://routing.openstreetmap.de/routed-";
	static final String UA = "TourWeather/0.5 (student project; Java HttpClient)";
	static final long TTL = 24 * 60 * 60 * 1000L;
	static final long GAP_MS = 1100;
	static final long FAIL_PAUSE = 60 * 1000L;
	static final double WALK_MAX_M = 1500;
	static final int CAR_EXTRA_MIN = 3; // 타고 내리고 세우는 시간 (OSRM 차 시간은 막힘 없는 값)
	static final int MAX_POINTS = 10; // 내 코스 최대 곳 수(CourseStore.MAX)와 같게

	private static final Map<String, Object[]> cache = new ConcurrentHashMap<>(); // 주소 키 → { 응답 본문, 만료 시각 }
	private static long lastCall = 0;
	private static long pausedUntil = 0;

	public static class Leg {
		private final String mode;          // "foot" · "car"
		private final double distM;
		private final double durS;
		private final List<double[]> path;  // [위도, 경도]

		Leg(String mode, double distM, double durS, List<double[]> path) {
			this.mode = mode;
			this.distM = distM;
			this.durS = durS;
			this.path = path;
		}

		public String getMode() { return mode; }
		public boolean isWalk() { return "foot".equals(mode); }
		public double getKm() { return Math.round(distM / 100.0) / 10.0; }
		public List<double[]> getPath() { return path; }

		// 걸어서는 그대로, 차는 + 3분. 10분까지 1분 단위, 그 위는 5분 단위
		public int getMinutes() {
			int m = (int) Math.round(durS / 60.0) + (isWalk() ? 0 : CAR_EXTRA_MIN);
			m = Math.max(1, m);
			return m <= 10 ? m : (int) (Math.round(m / 5.0) * 5);
		}

		public String getText() {
			int m = getMinutes();
			String t = m >= 60 ? (m / 60) + "시간" + (m % 60 == 0 ? "" : " " + (m % 60) + "분") : m + "분";
			return (isWalk() ? "걸어서 " : "차로 약 ") + t;
		}

		// "450m" · "2.7km"
		public String getDistText() {
			return distM < 1000 ? Math.round(distM / 10.0) * 10 + "m" : String.format(Locale.ROOT, "%.1fkm", distM / 1000.0);
		}
	}

	public static class Result {
		private final List<Leg> legs;
		Result(List<Leg> legs) { this.legs = legs; }
		public List<Leg> getLegs() { return legs; }
		public double getKm() {
			double m = 0;
			for (Leg l : legs) m += l.distM;
			return Math.round(m / 100.0) / 10.0;
		}
	}

	// pts = [위도, 경도] 순서대로 2~8개. 못 구하면 null
	public static Result through(List<double[]> pts) {
		if (pts == null || pts.size() < 2 || pts.size() > MAX_POINTS) return null;
		List<Leg> legs = legs("foot", pts);
		if (legs == null) return null;
		boolean far = false;
		for (Leg l : legs) if (l.distM > WALK_MAX_M) far = true;
		if (far) {
			List<Leg> car = legs("car", pts);
			if (car != null && car.size() == legs.size()) {
				for (int i = 0; i < legs.size(); i++) {
					if (legs.get(i).distM > WALK_MAX_M) legs.set(i, car.get(i));
				}
			}
		}
		return new Result(legs);
	}

	private static List<Leg> legs(String profile, List<double[]> pts) {
		StringBuilder c = new StringBuilder();
		for (double[] p : pts) {
			if (c.length() > 0) c.append(';');
			c.append(String.format(Locale.ROOT, "%.5f,%.5f", p[1], p[0])); // OSRM 은 경도,위도
		}
		String url = BASE + profile + "/route/v1/driving/" + c + "?overview=false&steps=true&geometries=geojson";
		String body = cached(url);
		boolean fresh = false;
		if (body == null) {
			synchronized (RoadRoute.class) {
				body = cached(url);
				if (body == null) {
					long now = System.currentTimeMillis();
					if (now < pausedUntil) return null;
					long wait = lastCall + GAP_MS - now;
					try {
						if (wait > 0) Thread.sleep(wait);
						lastCall = System.currentTimeMillis();
						String[] r = ApiClient.getPlain(url, UA, 6);
						if (!"200".equals(r[0])) {
							pausedUntil = System.currentTimeMillis() + FAIL_PAUSE;
							System.out.println("[TourWeather] 길 찾기 HTTP " + r[0]);
							return null;
						}
						body = r[1];
						fresh = true;
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return null;
					} catch (IOException | IllegalArgumentException e) {
						pausedUntil = System.currentTimeMillis() + FAIL_PAUSE;
						System.out.println("[TourWeather] 길 찾기 실패: " + e.getMessage());
						return null;
					}
				}
			}
		}
		List<Leg> legs = parse(profile, body, pts.size() - 1);
		if (legs != null && fresh) remember(url, body); // 받은 지 24시간이 지나면 다시 묻는다
		return legs;
	}

	// 응답 → 구간마다 거리 · 시간 · 경로(걸음마다 나온 선을 이어 붙임). 모양이 다르면 null
	static List<Leg> parse(String profile, String body, int want) {
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (!"Ok".equals(root.get("code").getAsString())) return null;
			JsonArray legs = root.getAsJsonArray("routes").get(0).getAsJsonObject().getAsJsonArray("legs");
			if (legs.size() != want) return null;
			List<Leg> out = new ArrayList<>();
			for (JsonElement le : legs) {
				JsonObject l = le.getAsJsonObject();
				List<double[]> path = new ArrayList<>();
				for (JsonElement se : l.getAsJsonArray("steps")) {
					JsonArray coords = se.getAsJsonObject().getAsJsonObject("geometry").getAsJsonArray("coordinates");
					for (JsonElement ce : coords) {
						JsonArray xy = ce.getAsJsonArray();
						double[] p = { xy.get(1).getAsDouble(), xy.get(0).getAsDouble() };
						double[] last = path.isEmpty() ? null : path.get(path.size() - 1);
						if (last == null || last[0] != p[0] || last[1] != p[1]) path.add(p);
					}
				}
				out.add(new Leg(profile, l.get("distance").getAsDouble(), l.get("duration").getAsDouble(), path));
			}
			return out;
		} catch (Exception e) {
			System.out.println("[TourWeather] 길 찾기 응답을 못 읽음: " + e.getMessage());
			return null;
		}
	}

	private static String cached(String url) {
		Object[] e = cache.get(url);
		if (e == null) return null;
		if (System.currentTimeMillis() > (Long) e[1]) {
			cache.remove(url);
			return null;
		}
		return (String) e[0];
	}

	private static void remember(String url, String body) {
		if (cache.size() > 400) { // 오래 켜 둬도 메모리가 늘지 않게
			long now = System.currentTimeMillis();
			cache.entrySet().removeIf(x -> now > (Long) x.getValue()[1]);
			if (cache.size() > 400) cache.clear();
		}
		cache.put(url, new Object[] { body, System.currentTimeMillis() + TTL });
	}
}
