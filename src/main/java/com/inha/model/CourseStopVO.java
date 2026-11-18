package com.inha.model;

// /course 화면의 한 줄: 담은 곳 + 그날 날씨 + 다음 곳까지 거리
public class CourseStopVO {
	private int no;                 // 1부터 시작하는 순서
	private CourseItem item;
	private WeatherVO weather;      // 그 좌표(격자)의 하루 날씨. 못 구하면 null
	private String weatherError;    // 날씨를 못 구한 이유 (정상이면 "")
	private String warn;            // 실외인데 비 예보면 경고 문구 (아니면 "")
	private double nextKm = -1;     // 다음 곳까지 직선거리(km). 마지막이거나 좌표가 없으면 -1
	private boolean first;
	private boolean last;
	private ScoreVO score;          // 이 곳 · 그 날의 여행 지수 (못 만들면 null)
	private int startMin = -1;      // 예상 시간(하루 중 분). IcsBuilder.times 가 정한 값이라 캘린더(.ics)와 같다
	private int endMin = -1;
	private Double crowdRate;       // 이 관광지 이름으로 맞춘 붐빔 집중률 (없으면 null)

	private String eventNotice = "";   // 행사인데 고른 날이 기간 밖이면 안내 문구 (아니면 "")
	public String getEventNotice() { return eventNotice; }
	public void setEventNotice(String eventNotice) { this.eventNotice = eventNotice == null ? "" : eventNotice; }
	public int getStartMin() { return startMin; }
	public int getEndMin() { return endMin; }
	public void setTime(int startMin, int endMin) { this.startMin = startMin; this.endMin = endMin; }
	public ScoreVO getScore() { return score; }
	public void setScore(ScoreVO score) { this.score = score; }
	public Double getCrowdRate() { return crowdRate; }
	public void setCrowdRate(Double crowdRate) { this.crowdRate = crowdRate; }
	public String getCrowdText() {
		return crowdRate == null ? "" : "붐빔 " + Math.round(crowdRate) + "% (" + CrowdVO.levelText(crowdRate) + ")";
	}
	public int getNo() { return no; }
	public void setNo(int no) { this.no = no; }
	public CourseItem getItem() { return item; }
	public void setItem(CourseItem item) { this.item = item; }
	public WeatherVO getWeather() { return weather; }
	public void setWeather(WeatherVO weather) { this.weather = weather; }
	public String getWeatherError() { return weatherError; }
	public void setWeatherError(String weatherError) { this.weatherError = weatherError; }
	public String getWarn() { return warn; }
	public void setWarn(String warn) { this.warn = warn; }
	public double getNextKm() { return nextKm; }
	public void setNextKm(double nextKm) { this.nextKm = nextKm; }
	public boolean isFirst() { return first; }
	public void setFirst(boolean first) { this.first = first; }
	public boolean isLast() { return last; }
	public void setLast(boolean last) { this.last = last; }
}
