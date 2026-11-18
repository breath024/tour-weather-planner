package com.inha.model;

// 상세 사진 갤러리 한 장 (TourAPI detailImage2)
public class ImageVO {
	private String original;   // 큰 사진 URL
	private String small;      // 작은 사진 URL
	private String name;       // 사진 이름 (alt 로 사용)

	public String getOriginal() { return original; }
	public void setOriginal(String original) { this.original = original; }
	public String getSmall() { return small; }
	public void setSmall(String small) { this.small = small; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
}
