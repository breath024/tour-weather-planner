package com.inha.model;

// 지도 계산 도우미
public class Geo {

	private static final double EARTH_KM = 6371.0088; // 지구 반지름(km)

	// 두 좌표 사이 직선거리(km) — 하버사인 공식
	public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return EARTH_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	// ---------- 이동 시간 어림 (길 찾기 API 없이 직선거리로만 어림한다 → 화면에 「어림」이라고 밝힌다) ----------
	// 실제 길은 직선보다 돌아가므로 1.3배로 잡는다. 1.5km(길 기준) 안은 걸어서 시속 4.5km,
	// 그보다 멀면 차 · 버스로 도심 평균 시속 25km + 타고 내리는 데 5분.
	static final double ROAD_FACTOR = 1.3;
	static final double WALK_MAX_KM = 1.5;

	// 30m 안이면 같은자리로 봄
	static final double SAME_KM = 0.03;

	public static boolean samePlace(double straightKm) {
		return straightKm >= 0 && straightKm < SAME_KM;
	}

	public static boolean walkable(double straightKm) {
		return straightKm >= 0 && straightKm * ROAD_FACTOR <= WALK_MAX_KM;
	}

	// 어림 이동 시간(분). 10분까지는 1분 단위, 그 위는 5분 단위로 반올림
	public static int moveMinutes(double straightKm) {
		if (straightKm < 0) return 0;
		double road = straightKm * ROAD_FACTOR;
		double min = walkable(straightKm) ? road / 4.5 * 60 : road / 25.0 * 60 + 5;
		int m = (int) Math.round(Math.max(1, min));
		return m <= 10 ? m : (int) (Math.round(m / 5.0) * 5);
	}

	// "걸어서 약 4분" · "차로 약 20분" (km 가 음수면 "")
	public static String moveText(double straightKm) {
		if (straightKm < 0) return "";
		int m = moveMinutes(straightKm);
		String t = m >= 60 ? (m / 60) + "시간" + (m % 60 == 0 ? "" : " " + (m % 60) + "분") : m + "분";
		return (walkable(straightKm) ? "걸어서 약 " : "차로 약 ") + t;
	}
}
