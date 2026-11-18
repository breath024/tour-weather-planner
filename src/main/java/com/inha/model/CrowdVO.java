package com.inha.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

// 관광지 집중률(붐빔) 예측 — 한 시군구의 관광지별 · 날짜별 값 (오늘부터 약 30일)
// 집중률(cnctrRate)은 0~100 숫자로, 클수록 그날 그 관광지에 사람이 몰린다는 뜻이다.
public class CrowdVO {
	private boolean available;     // 값이 있나 (광주·전남 같은 자료 없는 지역, 0건이면 false)
	private String message = "";   // available 가 false 일 때 보여 줄 문구
	private String sigunguName = ""; // 응답의 시군구 이름 (예: 경주시)
	private boolean truncated;     // 한 번에 받는 한도(3000행)를 넘어 일부 관광지를 못 받았나
	// 관광지 이름 → (날짜 yyyyMMdd → 집중률). 이름은 응답 그대로
	private final Map<String, Map<String, Double>> sites = new LinkedHashMap<>();
	// 날짜 yyyyMMdd → 시군구 전체 평균 (여행 지수에 쓴다)
	private final Map<String, Double> average = new TreeMap<>();

	public static CrowdVO unavailable(String message) {
		CrowdVO v = new CrowdVO();
		v.available = false;
		v.message = message;
		return v;
	}

	// 값 하나를 쌓는다 (CrowdDAO 가 응답을 읽으며 부른다)
	void put(String site, String ymd, double rate) {
		sites.computeIfAbsent(site, k -> new TreeMap<>()).put(ymd, rate);
	}

	// 다 쌓은 뒤 날짜별 평균을 계산한다
	void finish() {
		Map<String, double[]> sum = new TreeMap<>();
		for (Map<String, Double> byDate : sites.values()) {
			for (Map.Entry<String, Double> e : byDate.entrySet()) {
				double[] s = sum.computeIfAbsent(e.getKey(), k -> new double[2]);
				s[0] += e.getValue();
				s[1]++;
			}
		}
		for (Map.Entry<String, double[]> e : sum.entrySet()) {
			average.put(e.getKey(), e.getValue()[0] / e.getValue()[1]);
		}
		available = !sites.isEmpty();
	}

	// 그 날(yyyyMMdd)의 시군구 전체 평균 집중률 (자료가 없으면 null)
	public Double averageOn(String ymd) {
		return average.get(ymd);
	}

	// 그 날의 관광지 이름 맞춤 값 (맞는 이름이 없거나 그 날 값이 없으면 null)
	public Double rateOn(String spotTitle, String ymd) {
		Map<String, Double> m = ratesFor(spotTitle);
		return m == null ? null : m.get(ymd);
	}

	// 관광지 이름과 맞는 날짜별 값 (맞는 이름이 없으면 null)
	public Map<String, Double> ratesFor(String spotTitle) {
		String name = matchedName(spotTitle);
		return name == null ? null : sites.get(name);
	}

	// 응답에 있는 관광지 이름 중 spotTitle 과 맞는 것. 공백 · 괄호 · 기호를 뺀 뒤 서로 포함하면 맞는 것으로 본다.
	// 단 관광지 이름이 집중률 이름을 품은 경우("황리단길 생활문화센터" ⊃ "황리단길")는 큰 장소 안의 작은 시설이라
	// 큰 장소의 붐빔을 그대로 받으면 안 된다 → 집중률 이름이 관광지 이름의 70% 이상일 때만 맞는 것으로 본다 (지역 이름 "경주" 는 뗀 뒤 잰다).
	// 여럿이면 이름 길이가 가장 비슷한 것 (예: "불국사" 와 "경주 불국사 [유네스코 세계유산]")
	public String matchedName(String spotTitle) {
		String t = normalize(spotTitle);
		if (t.length() < 3) return null; // 두 글자 이름("전주" 등)은 너무 흔해서 엉뚱한 곳과 맞기 쉬워 뺀다
		String tShort = stripRegion(t);
		String best = null;
		int bestGap = Integer.MAX_VALUE;
		for (String site : sites.keySet()) {
			String s = normalize(site);
			if (s.length() < 3) continue;
			boolean ok = s.contains(t);
			if (!ok && t.contains(s)) {
				String sShort = stripRegion(s);
				ok = sShort.length() * 10 >= tShort.length() * 7;
			}
			if (ok) {
				int gap = Math.abs(s.length() - t.length());
				if (gap < bestGap) {
					best = site;
					bestGap = gap;
				}
			}
		}
		return best;
	}

	// 이름 맨 앞의 시군구 이름("경주")을 뗀다: "경주남산" → "남산". 떼고 두 글자 미만이 남으면 그대로
	private String stripRegion(String name) {
		String stem = sigunguName.replaceAll("[시군구]$", "");
		if (stem.length() >= 2 && name.startsWith(stem) && name.length() - stem.length() >= 2) {
			return name.substring(stem.length());
		}
		return name;
	}

	// [..] (..) 안의 글을 빼고, 글자와 숫자만 남긴다
	public static String normalize(String s) {
		if (s == null) return "";
		return s.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", "").replaceAll("[^0-9A-Za-z가-힣]", "");
	}

	// 집중률 → 말. 공식 구간이 아니라 이 앱이 정한 구간이다: 40 이하 여유 · 60 이하 보통 · 80 이하 붐빔 · 그 위 매우 붐빔
	public static String levelText(double rate) {
		if (rate <= 40) return "여유";
		if (rate <= 60) return "보통";
		if (rate <= 80) return "붐빔";
		return "매우 붐빔";
	}

	public boolean isAvailable() { return available; }
	public String getMessage() { return message; }
	public String getSigunguName() { return sigunguName; }
	void setSigunguName(String sigunguName) { this.sigunguName = sigunguName; }
	public boolean isTruncated() { return truncated; }
	void setTruncated(boolean truncated) { this.truncated = truncated; }
	// 응답에 있는 관광지 수
	public int getSiteCount() { return sites.size(); }
	// 날짜(yyyyMMdd) 순 평균 전체
	public Map<String, Double> getAverage() { return average; }
}
