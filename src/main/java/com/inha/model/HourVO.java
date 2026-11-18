package com.inha.model;

// 시간별 예보 한 칸
public class HourVO {
	private String time;    // "09시"
	private String temp;    // 기온(℃)
	private int pop;        // 강수확률(%)
	private String sky;     // 맑음 · 구름많음 · 흐림 · 비 ...
	private String icon;    // 아이콘 이름표(sun · rain …)

	// "09시" → 9 (읽지 못하면 -1)
	public int getHourOfDay() {
		try {
			return Integer.parseInt(time.substring(0, 2));
		} catch (Exception e) {
			return -1;
		}
	}

	// 기온 숫자 ("-" 처럼 없으면 null)
	public Integer getTempValue() {
		try {
			return Integer.valueOf(temp.trim());
		} catch (Exception e) {
			return null;
		}
	}

	// 비 · 눈 · 소나기 하늘인가 (WeatherDAO 가 강수형태가 있으면 sky 칸에 그 이름을 넣는다)
	public boolean isRainSky() {
		return "비".equals(sky) || "비/눈".equals(sky) || "눈".equals(sky) || "소나기".equals(sky);
	}

	public String getTime() { return time; }
	public void setTime(String time) { this.time = time; }
	public String getTemp() { return temp; }
	public void setTemp(String temp) { this.temp = temp; }
	public int getPop() { return pop; }
	public void setPop(int pop) { this.pop = pop; }
	public String getSky() { return sky; }
	public void setSky(String sky) { this.sky = sky; }
	public String getIcon() { return icon; }
	public void setIcon(String icon) { this.icon = icon; }
}
