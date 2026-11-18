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
}
