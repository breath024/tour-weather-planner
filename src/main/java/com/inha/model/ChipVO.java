package com.inha.model;

// 여행 지수의 근거 칩 하나 (날씨 · 미세먼지 · 자외선 · 붐빔 · 특보 중 하나)
public class ChipVO {
	public static final String GOOD = "good";   // 점수에 거의 영향 없음
	public static final String OK = "ok";       // 조금 깎임
	public static final String BAD = "bad";     // 많이 깎임
	public static final String NONE = "none";   // 자료 없음 (회색)

	private String key = "";     // weather · dust · uv · crowd · warn
	private String label = "";   // 화면 이름: 날씨 · 미세먼지 · 자외선 · 붐빔 · 특보
	private String text = "";    // 칩 글자: "맑음 · 1° ~ 8°" 또는 "자료 없음"
	private String level = NONE; // good · ok · bad · none
	private int delta;           // 깎인 점수 (0 또는 음수)
	private String hint = "";    // 자세한 설명 · 자료가 없는 이유 (마우스를 올리면 보이는 글)
	private int cap = 100;       // 특보 칩만: 이 칩이 거는 점수 상한 (제한이 없으면 100)

	public ChipVO() {
	}

	public ChipVO(String key, String label, String text, String level, int delta, String hint) {
		this.key = key;
		this.label = label;
		this.text = text;
		this.level = level;
		this.delta = delta;
		this.hint = hint == null ? "" : hint;
	}

	// 특보 칩처럼 점수 상한을 거는 칩
	public ChipVO(String key, String label, String text, String level, String hint, int cap) {
		this(key, label, text, level, 0, hint);
		this.cap = cap;
	}

	public boolean isMissing() { return NONE.equals(level); }

	public String getKey() { return key; }
	public String getLabel() { return label; }
	public String getText() { return text; }
	public String getLevel() { return level; }
	public int getDelta() { return delta; }
	public String getHint() { return hint; }
	public int getCap() { return cap; }
}
