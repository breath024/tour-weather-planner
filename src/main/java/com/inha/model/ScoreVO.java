package com.inha.model;

import java.util.ArrayList;
import java.util.List;

// 여행 지수 결과: 0~100 점수 + 등급 + 근거 칩 (TravelScore 가 만든다)
public class ScoreVO {
	private boolean available;          // 점수를 냈나 (근거가 하나도 없으면 false)
	private int score;                  // 0~100
	private String grade = "";          // 최고 · 좋음 · 보통 · 비추 (점수가 없으면 "자료 없음")
	private String gradeKey = "none";   // best · good · ok · bad · none (CSS 색 구분용)
	private List<ChipVO> chips = new ArrayList<>();
	private int missing;                // 자료가 없는 근거 수
	private String note = "";           // 점수 아래 작은 글: 빠진 근거 · 특보 상한 설명
	private int cap = 100;              // 특보로 제한된 점수 상한 (제한이 없으면 100)

	// ---- 3판: 근거가 빠진 날의 불확실 표시 ----
	private boolean uncertain;          // 다른 날에는 있는 근거(미세먼지 · 자외선 · 특보 …)가 이 날은 없다
	private String uncertainNote = "";  // 짧은 설명: "중기예보 · 미세먼지 · 자외선 · 특보 빠짐"
	private int rawScore;               // 상한(불확실 · 특보)을 적용하기 전 점수

	public boolean isUncertain() { return uncertain; }
	public void setUncertain(boolean uncertain) { this.uncertain = uncertain; }
	public String getUncertainNote() { return uncertainNote; }
	public void setUncertainNote(String uncertainNote) { this.uncertainNote = uncertainNote == null ? "" : uncertainNote; }
	public int getRawScore() { return rawScore; }
	public void setRawScore(int rawScore) { this.rawScore = rawScore; }
	// 점수를 믿을 만한 정도: 근거가 빠진 날은 「낮음」
	public String getConfidence() { return !available ? "" : (uncertain ? "낮음" : "보통 이상"); }

	// 근거 5개 중 쓴 개수
	public int getBasis() { return chips.size() - missing; }
	public int getBasisTotal() { return chips.size(); }

	// 칩 하나 찾기 (없으면 null)
	public ChipVO chip(String key) {
		for (ChipVO c : chips) if (c.getKey().equals(key)) return c;
		return null;
	}

	public boolean isAvailable() { return available; }
	public void setAvailable(boolean available) { this.available = available; }
	public int getScore() { return score; }
	public void setScore(int score) { this.score = score; }
	public String getGrade() { return grade; }
	public void setGrade(String grade) { this.grade = grade; }
	public String getGradeKey() { return gradeKey; }
	public void setGradeKey(String gradeKey) { this.gradeKey = gradeKey; }
	public List<ChipVO> getChips() { return chips; }
	public void setChips(List<ChipVO> chips) { this.chips = chips; }
	public int getMissing() { return missing; }
	public void setMissing(int missing) { this.missing = missing; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
	public int getCap() { return cap; }
	public void setCap(int cap) { this.cap = cap; }
}
