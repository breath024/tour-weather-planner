package com.inha.model;

// 하루 코스의 한 칸: 2시간 블록 + 그 시간대 날씨 + 갈 관광지
public class PlanBlockVO {
	private int no;               // 1부터
	private int startHour;        // 10
	private int endHour;          // 12
	private String timeLabel;     // "10:00–12:00"
	private boolean good;         // 가운데 지점 예보로 본 판정: true 실외 좋음, false 실내 권장
	private String kind;          // "실외 좋음" · "실내 권장"
	private String reason;        // 실내 권장인 이유 (실외 좋음이면 "")
	private TourVO spot;          // 이 시간대에 갈 곳
	private boolean fallback;     // 블록 판정과 다른 종류(실내/실외)로 채웠나
	private String icon;          // 이 곳 격자 날씨 (그 시간대) 아이콘 · 하늘
	private String sky;
	private String temp;          // "12°" (두 시간 평균)
	private int pop;              // 강수확률 최대(%)
	private boolean approx;       // 이 곳 날씨가 가까운 격자 값을 빌린 것인가
	private String warn;          // 실외인데 이 곳 날씨가 나쁘면 안내 (아니면 "")
	private double nextKm = -1;   // 다음 곳까지 직선거리(km), 마지막이면 -1
	private boolean last;

	// ---- 2판: 중기예보 날(오전 · 오후 2칸) · 일몰 ----
	private boolean mid;          // 중기예보 날의 오전 · 오후 칸인가 (시간별 예보가 없다)
	private String period = "";   // 중기 칸 이름: "오전" · "오후" (시간별 칸은 "")
	private String midProblem = ""; // 중기 칸의 나쁜 이유 (좋으면 ""). 중기는 지역 단위라 모든 관광지에 같다
	private String phase = "DAY"; // DAY(해 있는 시간) · SUNSET(일몰이 걸친 칸) · NIGHT(해 진 뒤)
	private boolean sunsetCut;    // 실외 칸을 일몰 시각에 맞춰 줄였나
	private String sunsetNote = ""; // 일몰 관련 안내 ("일몰 17:14 직전 — 노을 보기 좋은 곳")

	// ---- 3판: 비 오면 플랜 B ----
	private TourVO alt;               // 실외 칸의 가까운 실내 대안 한 곳 (5km 안, 없으면 null)
	private TourVO inCourseAlt;         // 코스 밖 대안이 없을 때 알려 줄, 이미 코스에 든 가까운 실내 관광지 (5km 안, 없으면 null)
	private int inCourseAltNo;           // 그 곳의 코스 번호(1부터)
	private double inCourseAltKm = -1;
	private double altKm = -1;        // 그 대안까지 직선거리(km)
	private boolean rainRisk;         // 이 칸 시간에 비 위험이 있나 (강수형태가 있거나 강수확률 30% 이상)

	// 플랜 B 를 붙일 칸인가: 고른 곳이 실외일 때만 (실내는 비가 와도 그대로 가면 된다)
	public boolean isOutdoorSpot() { return spot != null && !spot.isIndoor(); }
	public boolean isHasInCourseAlt() { return inCourseAlt != null; }
	public TourVO getInCourseAlt() { return inCourseAlt; }
	public void setInCourseAlt(TourVO v) { this.inCourseAlt = v; }
	public int getInCourseAltNo() { return inCourseAltNo; }
	public void setInCourseAltNo(int n) { this.inCourseAltNo = n; }
	public double getInCourseAltKm() { return inCourseAltKm; }
	public void setInCourseAltKm(double km) { this.inCourseAltKm = km; }
	public boolean isHasAlt() { return alt != null; }
	public TourVO getAlt() { return alt; }
	public void setAlt(TourVO alt) { this.alt = alt; }
	public double getAltKm() { return altKm; }
	public void setAltKm(double altKm) { this.altKm = altKm; }
	public boolean isRainRisk() { return rainRisk; }
	public void setRainRisk(boolean rainRisk) { this.rainRisk = rainRisk; }

	public boolean isMid() { return mid; }
	public void setMid(boolean mid) { this.mid = mid; }
	public String getPeriod() { return period; }
	public void setPeriod(String period) { this.period = period; }
	public String getMidProblem() { return midProblem; }
	public void setMidProblem(String midProblem) { this.midProblem = midProblem; }
	public String getPhase() { return phase; }
	public void setPhase(String phase) { this.phase = phase; }
	public boolean isNight() { return "NIGHT".equals(phase); }
	public boolean isSunsetBlock() { return "SUNSET".equals(phase); }
	public boolean isSunsetCut() { return sunsetCut; }
	public void setSunsetCut(boolean sunsetCut) { this.sunsetCut = sunsetCut; }
	public String getSunsetNote() { return sunsetNote; }
	public void setSunsetNote(String sunsetNote) { this.sunsetNote = sunsetNote; }
	public int getNo() { return no; }
	public void setNo(int no) { this.no = no; }
	public int getStartHour() { return startHour; }
	public void setStartHour(int startHour) { this.startHour = startHour; }
	public int getEndHour() { return endHour; }
	public void setEndHour(int endHour) { this.endHour = endHour; }
	public String getTimeLabel() { return timeLabel; }
	public void setTimeLabel(String timeLabel) { this.timeLabel = timeLabel; }
	public boolean isGood() { return good; }
	public void setGood(boolean good) { this.good = good; }
	public String getKind() { return kind; }
	public void setKind(String kind) { this.kind = kind; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public TourVO getSpot() { return spot; }
	public void setSpot(TourVO spot) { this.spot = spot; }
	public boolean isFallback() { return fallback; }
	public void setFallback(boolean fallback) { this.fallback = fallback; }
	public String getIcon() { return icon; }
	public void setIcon(String icon) { this.icon = icon; }
	public String getSky() { return sky; }
	public void setSky(String sky) { this.sky = sky; }
	public String getTemp() { return temp; }
	public void setTemp(String temp) { this.temp = temp; }
	public int getPop() { return pop; }
	public void setPop(int pop) { this.pop = pop; }
	public boolean isApprox() { return approx; }
	public void setApprox(boolean approx) { this.approx = approx; }
	public String getWarn() { return warn; }
	public void setWarn(String warn) { this.warn = warn; }
	public double getNextKm() { return nextKm; }
	public void setNextKm(double nextKm) { this.nextKm = nextKm; }
	public boolean isLast() { return last; }
	public void setLast(boolean last) { this.last = last; }
}
