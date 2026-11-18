package com.inha.model;

import java.util.ArrayList;
import java.util.List;

// 날씨 맞춤 하루 코스 결과 (CoursePlanner 가 만든다)
public class DayPlanVO {
	private boolean available;     // 코스를 짰나 (true 면 blocks 를 보여 준다)
	private boolean tooLate;       // 오늘인데 남은 시간대가 2개 미만 → "내일 코스 보기" 링크를 보여 줄 것
	private String message;        // available 이 false 일 때 안내 문구
	private String note;           // 코스에 대한 덧붙임 (넓은 지역 · 부족해서 채움 등). 없으면 ""
	private List<PlanBlockVO> blocks = new ArrayList<>();
	private double totalKm;        // 코스 직선거리 합 (소수 1자리)
	private boolean mid;           // 중기예보 날이라 오전 · 오후 2칸으로만 짰나
	private String sunrise = "";   // 해 뜨는 · 지는 시각 "07:02" (모르면 "")
	private String sunset = "";
	private String ids = "";       // 담기 폼용 "id1,id2,..." (순서대로)

	public int getSize() { return blocks.size(); }
	public boolean isHasFallback() {
		for (PlanBlockVO b : blocks) if (b.isFallback()) return true;
		return false;
	}

	public boolean isMid() { return mid; }
	public void setMid(boolean mid) { this.mid = mid; }
	public String getSunrise() { return sunrise; }
	public void setSunrise(String sunrise) { this.sunrise = sunrise; }
	public String getSunset() { return sunset; }
	public void setSunset(String sunset) { this.sunset = sunset; }
	public boolean isAvailable() { return available; }
	public void setAvailable(boolean available) { this.available = available; }
	public boolean isTooLate() { return tooLate; }
	public void setTooLate(boolean tooLate) { this.tooLate = tooLate; }
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
	public List<PlanBlockVO> getBlocks() { return blocks; }
	public void setBlocks(List<PlanBlockVO> blocks) { this.blocks = blocks; }
	public double getTotalKm() { return totalKm; }
	public void setTotalKm(double totalKm) { this.totalKm = totalKm; }
	public String getIds() { return ids; }
	public void setIds(String ids) { this.ids = ids; }
}
