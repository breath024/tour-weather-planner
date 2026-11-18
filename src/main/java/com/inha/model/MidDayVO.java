package com.inha.model;

import java.time.LocalDate;

// 기상청 중기예보 하루 한 칸 (4~10일 뒤). 육상(하늘 · 강수확률) + 기온을 합친 값
// 4~7일 뒤는 오전 · 오후가 따로 오고, 8~10일 뒤는 하루 값 하나라 오전 · 오후에 같은 값을 넣는다
public class MidDayVO {
	private LocalDate date;     // 그 날짜
	private String amWf = "";   // 오전 날씨 문장 ("맑음", "구름많고 비", "흐리고 눈" …)
	private String pmWf = "";
	private Integer amPop;      // 오전 강수확률(%), 없으면 null
	private Integer pmPop;
	private Integer minTemp;    // 최저기온, 없으면 null
	private Integer maxTemp;
	private String issued = ""; // 발표 시각 "202611180600"

	// 이 날의 육상 자료(날씨 문장)가 있나
	public boolean isHasLand() {
		return !amWf.isEmpty() || !pmWf.isEmpty();
	}

	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public String getAmWf() { return amWf; }
	public void setAmWf(String amWf) { this.amWf = amWf; }
	public String getPmWf() { return pmWf; }
	public void setPmWf(String pmWf) { this.pmWf = pmWf; }
	public Integer getAmPop() { return amPop; }
	public void setAmPop(Integer amPop) { this.amPop = amPop; }
	public Integer getPmPop() { return pmPop; }
	public void setPmPop(Integer pmPop) { this.pmPop = pmPop; }
	public Integer getMinTemp() { return minTemp; }
	public void setMinTemp(Integer minTemp) { this.minTemp = minTemp; }
	public Integer getMaxTemp() { return maxTemp; }
	public void setMaxTemp(Integer maxTemp) { this.maxTemp = maxTemp; }
	public String getIssued() { return issued; }
	public void setIssued(String issued) { this.issued = issued; }
}
