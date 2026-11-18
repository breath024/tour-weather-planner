package com.inha.model;

// 날씨 달력(가장 좋은 날 띠)의 하루 칸: 날짜 + 날씨 요약 + 그 날 지역 점수
public class DayScoreVO {
	private int dayOffset;          // 0~10
	private String dateLabel = "";  // "11/21(토)"
	private String dayName = "";    // 오늘 · 내일 · 모레 · 글피 · 4일뒤 …
	private String source = DayForecastVO.NONE; // SHORT · MID · NONE
	private String icon = "";
	private String sky = "";
	private String tempRange = "-";
	private int maxPop;
	private ScoreVO score;          // 이 날 지역 점수 (날씨가 없는 날은 available=false)
	private boolean best;           // 가장 좋은 날인가
	private boolean selected;       // 지금 보고 있는 날인가
	private String note = "";       // 중기예보 · 예보 없음 안내

	// ---- 3판: 공정한 비교 ----
	private int compareScore = -1;      // 「가장 좋은 날」 비교용 점수: 모든 날에 공통으로 있는 근거(날씨 · 붐빔)만으로 계산
	private String compareBasis = "";   // 비교에 쓴 근거 이름 "날씨 · 붐빔"

	public int getCompareScore() { return compareScore; }
	public void setCompareScore(int compareScore) { this.compareScore = compareScore; }
	public String getCompareBasis() { return compareBasis; }
	public void setCompareBasis(String compareBasis) { this.compareBasis = compareBasis == null ? "" : compareBasis; }
	public boolean isUncertain() { return score != null && score.isUncertain(); }
	public String getUncertainNote() { return score == null ? "" : score.getUncertainNote(); }

	public boolean isHasScore() { return score != null && score.isAvailable(); }

	public int getDayOffset() { return dayOffset; }
	public void setDayOffset(int dayOffset) { this.dayOffset = dayOffset; }
	public String getDateLabel() { return dateLabel; }
	public void setDateLabel(String dateLabel) { this.dateLabel = dateLabel; }
	public String getDayName() { return dayName; }
	public void setDayName(String dayName) { this.dayName = dayName; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public String getIcon() { return icon; }
	public void setIcon(String icon) { this.icon = icon; }
	public String getSky() { return sky; }
	public void setSky(String sky) { this.sky = sky; }
	public String getTempRange() { return tempRange; }
	public void setTempRange(String tempRange) { this.tempRange = tempRange; }
	public int getMaxPop() { return maxPop; }
	public void setMaxPop(int maxPop) { this.maxPop = maxPop; }
	public ScoreVO getScore() { return score; }
	public void setScore(ScoreVO score) { this.score = score; }
	public boolean isBest() { return best; }
	public void setBest(boolean best) { this.best = best; }
	public boolean isSelected() { return selected; }
	public void setSelected(boolean selected) { this.selected = selected; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
}
