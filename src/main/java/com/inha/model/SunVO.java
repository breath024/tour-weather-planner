package com.inha.model;

import java.time.LocalDate;

// 하루의 해 뜨는 · 지는 시각 (한국천문연구원 출몰시각)
public class SunVO {
	private LocalDate date;
	private String location = "";   // 응답이 알려 주는 가까운 지점 이름 (예: 경주)
	private String sunrise = "";    // "07:02" (없으면 "")
	private String sunset = "";     // "17:14"
	private String civilDawn = "";  // 시민박명 시작 (해 뜨기 전 하늘이 밝아지는 때)
	private String civilDusk = "";  // 시민박명 끝 (해 진 뒤 완전히 어두워지는 때)

	// "0702" → "07:02", 값이 없거나 "----" 면 ""
	static String clock(String raw) {
		if (raw == null) return "";
		String s = raw.trim();
		if (!s.matches("\\d{4}")) return "";
		return s.substring(0, 2) + ":" + s.substring(2);
	}

	// "17:14" → 1034 (자정부터의 분), 값이 없으면 -1
	static int minutes(String hhmm) {
		if (hhmm == null || !hhmm.matches("\\d{2}:\\d{2}")) return -1;
		return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3));
	}

	public int getSunriseMinutes() { return minutes(sunrise); }
	public int getSunsetMinutes() { return minutes(sunset); }
	// 해가 지는 시(hour)  예: 17:14 → 17 (없으면 -1)
	public int getSunsetHour() { return getSunsetMinutes() < 0 ? -1 : getSunsetMinutes() / 60; }
	public boolean isHasTimes() { return !sunrise.isEmpty() && !sunset.isEmpty(); }

	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public String getLocation() { return location; }
	public void setLocation(String location) { this.location = location; }
	public String getSunrise() { return sunrise; }
	public void setSunrise(String sunrise) { this.sunrise = sunrise; }
	public String getSunset() { return sunset; }
	public void setSunset(String sunset) { this.sunset = sunset; }
	public String getCivilDawn() { return civilDawn; }
	public void setCivilDawn(String civilDawn) { this.civilDawn = civilDawn; }
	public String getCivilDusk() { return civilDusk; }
	public void setCivilDusk(String civilDusk) { this.civilDusk = civilDusk; }
}
