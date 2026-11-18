package com.inha.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 하루 예보 요약 (0~10일 뒤 어느 날이든 같은 모양). 단기예보(0~3일, 시간별)와 중기예보(4~10일, 오전 · 오후)를 이어 붙인 결과
public class DayForecastVO {
	public static final String SHORT = "SHORT";  // 단기예보 (시간별)
	public static final String MID = "MID";      // 중기예보 (오전 · 오후)
	public static final String NONE = "NONE";    // 예보 없음

	private int dayOffset;          // 0 = 오늘 … 10
	private LocalDate date;
	private String dateLabel = "";  // "11/21(토)"
	private String source = NONE;   // SHORT · MID · NONE
	private boolean hourly;         // 시간별 예보(hours)가 있나
	private boolean partial;        // 일부 시간만 나와 있나 (오늘 지난 시간, 글피의 일부 등)
	private String note = "";       // 화면에 붙일 안내 ("중기예보라 시간별 예보는 없어요" 등)

	private Integer minTemp;        // 최저 · 최고 (없으면 null)
	private Integer maxTemp;
	private int maxPop;             // 하루 중 최대 강수확률(%)
	private String sky = "";        // 대표 하늘 (맑음 · 구름많음 · 흐림 · 비 · 눈 · 비/눈 · 소나기)
	private String icon = "";
	private String rainType = "";   // 비 · 눈 · 비/눈 · 소나기 (없으면 "")
	private boolean rainy;          // 비 · 눈 예보가 있거나 강수확률 60% 이상

	private String amSky = "";      // 오전(06~12시) 하늘과 강수확률 (없으면 "" 와 -1)
	private String amIcon = "";
	private int amPop = -1;
	private String pmSky = "";      // 오후(12~21시)
	private String pmIcon = "";
	private int pmPop = -1;

	private List<HourVO> hours = new ArrayList<>(); // 시간별 (SHORT 만)
	private WeatherVO weather;      // 1판 날씨 화면이 쓰는 WeatherVO 로 바꾼 것 (예보 없으면 null)

	public boolean isAvailable() { return !NONE.equals(source); }

	public String getTempRange() {
		if (minTemp == null || maxTemp == null) return "-";
		return minTemp + "° ~ " + maxTemp + "°";
	}

	public int getDayOffset() { return dayOffset; }
	public void setDayOffset(int dayOffset) { this.dayOffset = dayOffset; }
	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public String getDateLabel() { return dateLabel; }
	public void setDateLabel(String dateLabel) { this.dateLabel = dateLabel; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public boolean isHourly() { return hourly; }
	public void setHourly(boolean hourly) { this.hourly = hourly; }
	public boolean isPartial() { return partial; }
	public void setPartial(boolean partial) { this.partial = partial; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
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
	public String getAmSky() { return amSky; }
	public void setAmSky(String amSky) { this.amSky = amSky; }
	public String getAmIcon() { return amIcon; }
	public void setAmIcon(String amIcon) { this.amIcon = amIcon; }
	public int getAmPop() { return amPop; }
	public void setAmPop(int amPop) { this.amPop = amPop; }
	public String getPmSky() { return pmSky; }
	public void setPmSky(String pmSky) { this.pmSky = pmSky; }
	public String getPmIcon() { return pmIcon; }
	public void setPmIcon(String pmIcon) { this.pmIcon = pmIcon; }
	public int getPmPop() { return pmPop; }
	public void setPmPop(int pmPop) { this.pmPop = pmPop; }
	public List<HourVO> getHours() { return hours; }
	public void setHours(List<HourVO> hours) { this.hours = hours; }
	public WeatherVO getWeather() { return weather; }
	public void setWeather(WeatherVO weather) { this.weather = weather; }
}
