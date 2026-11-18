package com.inha.model;

import java.time.LocalDate;

// 에어코리아 미세먼지 예보 (하루 한 칸) — 지역 이름이 여럿이면(강원 영동 · 영서 등) 나쁜 쪽 등급을 쓴다
public class AirForecastVO {
	private LocalDate date;       // 예보 날짜
	private int pm10Grade;        // 1 좋음 · 2 보통 · 3 나쁨 · 4 매우나쁨 · 0 모름
	private int pm25Grade;
	private String issued = "";   // 발표 시각 "2026-11-18 17시 발표"
	private String cause = "";    // 예보 원인 문장 (informCause)
	private String overall = "";  // 예보 개황 문장 (informOverall)

	public int getGrade() { return Math.max(pm10Grade, pm25Grade); }
	public String getGradeText() { return AirVO.gradeText(getGrade()); }
	public String getPm10GradeText() { return AirVO.gradeText(pm10Grade); }
	public String getPm25GradeText() { return AirVO.gradeText(pm25Grade); }
	public boolean isBad() { return getGrade() >= 3; }

	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public int getPm10Grade() { return pm10Grade; }
	public void setPm10Grade(int pm10Grade) { this.pm10Grade = pm10Grade; }
	public int getPm25Grade() { return pm25Grade; }
	public void setPm25Grade(int pm25Grade) { this.pm25Grade = pm25Grade; }
	public String getIssued() { return issued; }
	public void setIssued(String issued) { this.issued = issued; }
	public String getCause() { return cause; }
	public void setCause(String cause) { this.cause = cause; }
	public String getOverall() { return overall; }
	public void setOverall(String overall) { this.overall = overall; }
}
