package com.inha.model;

import java.util.ArrayList;
import java.util.List;

// 하루치 날씨 요약 (기상청 단기예보 결과를 정리한 것)
public class WeatherVO {
	private String dateLabel;   // 11/17(화)
	private double lat;         // 조회에 쓴 좌표
	private double lon;
	private int nx;             // 기상청 격자
	private int ny;
	private String baseDate;    // 발표 일자 · 시각
	private String baseTime;

	private Integer minTemp;    // 최저기온
	private Integer maxTemp;    // 최고기온
	private int maxPop;         // 최대 강수확률(%)
	private String sky;         // 대표 하늘 상태
	private String icon;
	private String rainType;    // 비 · 눈 · 비/눈 · 소나기 (없으면 "")
	private boolean rainy;      // 비/눈 예보가 있나 (강수형태 있음 또는 강수확률 60% 이상)
	private String rainTime;    // 비가 시작되는 시각 (예: "15시")
	private boolean partial;    // 오늘이라 지난 시간은 빠졌나
	private boolean maxFromHours; // 최고기온이 기상청 값이 아니라 남은 시간의 기온에서 뽑은 값인가
	private List<HourVO> hours = new ArrayList<>();

	public String getTempRange() {
		if (minTemp == null || maxTemp == null) return "-";
		return minTemp + "° ~ " + maxTemp + "°";
	}

	public String getDateLabel() { return dateLabel; }
	public void setDateLabel(String dateLabel) { this.dateLabel = dateLabel; }
	public double getLat() { return lat; }
	public void setLat(double lat) { this.lat = lat; }
	public double getLon() { return lon; }
	public void setLon(double lon) { this.lon = lon; }
	public int getNx() { return nx; }
	public void setNx(int nx) { this.nx = nx; }
	public int getNy() { return ny; }
	public void setNy(int ny) { this.ny = ny; }
	public String getBaseDate() { return baseDate; }
	public void setBaseDate(String baseDate) { this.baseDate = baseDate; }
	public String getBaseTime() { return baseTime; }
	public void setBaseTime(String baseTime) { this.baseTime = baseTime; }
	public Integer getMinTemp() { return minTemp; }
	public void setMinTemp(Integer minTemp) { this.minTemp = minTemp; }
	public Integer getMaxTemp() { return maxTemp; }
	public void setMaxTemp(Integer maxTemp) { this.maxTemp = maxTemp; }
	public int getMaxPop() { return maxPop; }
	public void setMaxPop(int maxPop) { this.maxPop = maxPop; }
	public String getSky() { return sky; }
	public void setSky(String sky) { this.sky = sky; }
	public String getIcon() { return icon; }
	public void setIcon(String icon) { this.icon = icon; }
	public String getRainType() { return rainType; }
	public void setRainType(String rainType) { this.rainType = rainType; }
	public boolean isRainy() { return rainy; }
	public void setRainy(boolean rainy) { this.rainy = rainy; }
	public String getRainTime() { return rainTime; }
	public void setRainTime(String rainTime) { this.rainTime = rainTime; }
	public boolean isPartial() { return partial; }
	public void setPartial(boolean partial) { this.partial = partial; }
	public boolean isMaxFromHours() { return maxFromHours; }
	public void setMaxFromHours(boolean maxFromHours) { this.maxFromHours = maxFromHours; }
	public List<HourVO> getHours() { return hours; }
	public void setHours(List<HourVO> hours) { this.hours = hours; }
}
