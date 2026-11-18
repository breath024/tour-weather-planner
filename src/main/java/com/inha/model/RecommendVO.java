package com.inha.model;

// 날씨로 정한 추천 방향
public class RecommendVO {
	public static final String INDOOR = "INDOOR";
	public static final String OUTDOOR = "OUTDOOR";
	public static final String MIXED = "MIXED";

	private String mode;     // INDOOR · OUTDOOR · MIXED
	private String title;    // "실내 관광지 위주로 추천해요"
	private String reason;   // 왜 그렇게 정했는지
	private String clothes;  // 옷차림 한 줄
	private String umbrella; // 우산 안내 한 줄
	private String mask = "";  // 미세먼지가 나쁨 이상일 때 마스크 안내 한 줄 (아니면 "")

	public String getMode() { return mode; }
	public void setMode(String mode) { this.mode = mode; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public String getClothes() { return clothes; }
	public void setClothes(String clothes) { this.clothes = clothes; }
	public String getMask() { return mask; }
	public void setMask(String mask) { this.mask = mask; }
	public String getUmbrella() { return umbrella; }
	public void setUmbrella(String umbrella) { this.umbrella = umbrella; }
}
