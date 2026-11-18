package com.inha.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

// 기상청 생활기상지수(자외선지수) — 기준 시각부터 3시간 간격으로 약 3일치(h0 · h3 · … · h75)
// 오늘 낮 값이 지나간 시각에도 남도록 "오늘 0시 기준" 값과 "가장 최근 기준" 값을 시각별로 합쳐 둔다 (나중 값이 이김)
public class UvVO {
	private String areaNo = "";        // 조회에 쓴 지역 코드 (10자리)
	private boolean sidoFallback;      // 시군구 코드가 없어서 시도 단위로 대신 불렀나
	private LocalDateTime start;       // 가장 최근 기준 시각 (h0 의 시각)
	private final Map<LocalDateTime, Integer> values = new TreeMap<>(); // 시각 → 자외선 지수 (3시간 간격)

	void setValue(LocalDateTime at, int uv) {
		values.put(at, uv);
	}

	// 그 날짜에 들어가는 시각들 중 가장 큰 자외선 지수 (그 날 값이 하나도 없으면 null)
	public Integer maxOn(LocalDate date) {
		Integer max = null;
		for (Map.Entry<LocalDateTime, Integer> e : values.entrySet()) {
			if (!e.getKey().toLocalDate().equals(date)) continue;
			if (max == null || e.getValue() > max) max = e.getValue();
		}
		return max;
	}

	// 그 날 낮(09~15시 칸)의 가장 큰 자외선 지수. 낮 칸이 하나도 없으면 null
	// (예보가 새벽까지만 있는 날의 낮은 값을 "그 날 최대"로 보여 주면 거짓이라서 따로 뺐다)
	public Integer maxDaytimeOn(LocalDate date) {
		Integer max = null;
		for (Map.Entry<LocalDateTime, Integer> e : values.entrySet()) {
			if (!e.getKey().toLocalDate().equals(date)) continue;
			int h = e.getKey().getHour();
			if (h < 9 || h > 15) continue;
			if (max == null || e.getValue() > max) max = e.getValue();
		}
		return max;
	}

	// 단계 번호: 1 낮음(0~2) · 2 보통(3~5) · 3 높음(6~7) · 4 매우높음(8~10) · 5 위험(11 이상) (기상청 자외선지수 단계)
	public static int level(int uv) {
		if (uv <= 2) return 1;
		if (uv <= 5) return 2;
		if (uv <= 7) return 3;
		if (uv <= 10) return 4;
		return 5;
	}

	public static String levelText(int uv) {
		switch (level(uv)) {
		case 1: return "낮음";
		case 2: return "보통";
		case 3: return "높음";
		case 4: return "매우높음";
		default: return "위험";
		}
	}

	// 옷차림 · 선크림 안내 한 줄
	public static String advice(int uv) {
		switch (level(uv)) {
		case 1: return "자외선 걱정은 적어요.";
		case 2: return "한낮엔 선크림을 바르면 좋아요.";
		case 3: return "선크림과 모자를 챙기고, 한낮 야외는 그늘에서 쉬세요.";
		case 4: return "선크림 · 모자 · 선글라스가 꼭 필요해요. 한낮 야외는 피하세요.";
		default: return "한낮 야외 활동은 피하고, 나가면 자외선 차단을 철저히 하세요.";
		}
	}

	public String getAreaNo() { return areaNo; }
	public void setAreaNo(String areaNo) { this.areaNo = areaNo; }
	public boolean isSidoFallback() { return sidoFallback; }
	public void setSidoFallback(boolean sidoFallback) { this.sidoFallback = sidoFallback; }
	public LocalDateTime getStart() { return start; }
	public void setStart(LocalDateTime start) { this.start = start; }
}
