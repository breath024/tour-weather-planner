package com.inha.model;

import java.util.ArrayList;
import java.util.List;

// 오늘부터 10일 뒤까지(11칸) 하루 예보 요약 목록 + 어느 쪽 예보가 실패했는지
public class TenDayVO {
	private final List<DayForecastVO> days = new ArrayList<>(); // 0~10일 뒤 순서, 항상 11칸
	private String shortError = "";   // 단기예보를 못 불렀을 때 안내 문구 (정상이면 "")
	private String midError = "";     // 중기예보를 못 불렀을 때 안내 문구
	private String midIssued = "";    // 쓴 중기예보 발표 시각 "202611181800" (없으면 "")
	private String midTaCity = "";    // 중기 최저 · 최고기온을 가져온 도시 ("안동") — 고른 시군구와 다를 수 있어 화면에 밝힌다

	// n일 뒤 칸 (범위 밖이면 null)
	public DayForecastVO getDay(int n) {
		return n >= 0 && n < days.size() ? days.get(n) : null;
	}

	// 예보가 있는 날 수
	public int getAvailableCount() {
		int c = 0;
		for (DayForecastVO d : days) {
			if (d.isAvailable()) c++;
		}
		return c;
	}

	public List<DayForecastVO> getDays() { return days; }
	public String getShortError() { return shortError; }
	public void setShortError(String shortError) { this.shortError = shortError; }
	public String getMidError() { return midError; }
	public void setMidError(String midError) { this.midError = midError; }
	public String getMidTaCity() { return midTaCity; }
	public void setMidTaCity(String midTaCity) { this.midTaCity = midTaCity; }
	public String getMidIssued() { return midIssued; }
	public void setMidIssued(String midIssued) { this.midIssued = midIssued; }
}
