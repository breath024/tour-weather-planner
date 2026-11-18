package com.inha.model;

// 에어코리아 실시간 대기 (시도 안 측정소 평균) — 미세먼지 PM10 · 초미세먼지 PM2.5
public class AirVO {
	private String label = "";        // 예: "경북", "광주·전남"
	private String dataTime = "";     // 측정 시각 "2026-11-18 18:00"
	private Integer pm10;             // PM10 평균(㎍/㎥), 자료 없으면 null
	private Integer pm25;             // PM2.5 평균
	private int pm10Grade;            // 1 좋음 · 2 보통 · 3 나쁨 · 4 매우나쁨 · 0 모름
	private int pm25Grade;
	private int stationCount;         // 평균에 쓴 측정소 수
	private boolean stationMatched;   // 시군구 이름이 들어간 측정소만 골랐나 (false 면 시도 전체 평균)

	// 한국환경공단 통합대기환경지수 기준 등급 나누기
	public static int gradeOfPm10(int v) {
		if (v <= 30) return 1;
		if (v <= 80) return 2;
		if (v <= 150) return 3;
		return 4;
	}

	public static int gradeOfPm25(int v) {
		if (v <= 15) return 1;
		if (v <= 35) return 2;
		if (v <= 75) return 3;
		return 4;
	}

	public static String gradeText(int g) {
		switch (g) {
		case 1: return "좋음";
		case 2: return "보통";
		case 3: return "나쁨";
		case 4: return "매우나쁨";
		default: return "정보 없음";
		}
	}

	// 이 값이 어디 평균인지: "경주 측정소 평균 · 측정소 3곳" / "경북 평균 · 측정소 52곳"
	public String getScopeText() {
		return label + (stationMatched ? " 측정소 평균" : " 평균") + " · 측정소 " + stationCount + "곳";
	}

	public boolean isHasData() { return pm10 != null || pm25 != null; }
	// 둘 중 나쁜 쪽 등급
	public int getGrade() { return Math.max(pm10Grade, pm25Grade); }
	public String getGradeText() { return gradeText(getGrade()); }
	public String getPm10GradeText() { return gradeText(pm10Grade); }
	public String getPm25GradeText() { return gradeText(pm25Grade); }
	// 나쁨 이상이면 마스크 · 실내 쪽 안내
	public boolean isBad() { return getGrade() >= 3; }

	public String getLabel() { return label; }
	public void setLabel(String label) { this.label = label; }
	public String getDataTime() { return dataTime; }
	public void setDataTime(String dataTime) { this.dataTime = dataTime; }
	public Integer getPm10() { return pm10; }
	public void setPm10(Integer pm10) { this.pm10 = pm10; }
	public Integer getPm25() { return pm25; }
	public void setPm25(Integer pm25) { this.pm25 = pm25; }
	public int getPm10Grade() { return pm10Grade; }
	public void setPm10Grade(int pm10Grade) { this.pm10Grade = pm10Grade; }
	public int getPm25Grade() { return pm25Grade; }
	public void setPm25Grade(int pm25Grade) { this.pm25Grade = pm25Grade; }
	public int getStationCount() { return stationCount; }
	public void setStationCount(int stationCount) { this.stationCount = stationCount; }
	public boolean isStationMatched() { return stationMatched; }
	public void setStationMatched(boolean stationMatched) { this.stationMatched = stationMatched; }
}
