package com.inha.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// 기상특보 한 줄에서 시도 하나에 해당하는 부분
// 예: "강풍주의보" + 시도 "전라남도" + 세부지역 "여수, 영암, 함평, …"
public class WarningItem {
	private String kind = "";     // 특보 종류: "강풍주의보", "한파경보" … (예비특보는 "강풍 예비특보")
	private boolean prelim;       // 예비특보인가
	private String when = "";     // 예비특보의 예상 발효 때 ("11월 18일 오후(12시~18시)"), 현재 특보면 ""
	private String sido = "";     // 문장에 쓰인 시도 이름 (전라남도, 전북자치도, 서해5도 …)
	private String detail = "";   // 괄호 안 세부 지역 (없으면 "": 그 시도 전체)
	private LocalDate date;       // 예비특보가 발효될 날짜 ("11월 18일" → 11/18). 현재 특보 · 날짜를 못 읽으면 null

	// 경보인가 (주의보보다 위험한 단계)
	public boolean isWarning() {
		return !prelim && kind.endsWith("경보");
	}

	// 여행 지수 상한을 낮출 큰 특보인가: 호우 · 태풍 · 폭염 · 한파 · 대설 중 하나의 경보
	public boolean isMajor() {
		if (!isWarning()) return false;
		return kind.startsWith("호우") || kind.startsWith("태풍") || kind.startsWith("폭염")
				|| kind.startsWith("한파") || kind.startsWith("대설");
	}

	// 이 예비특보가 그 날짜에 해당하나? (날짜를 모르면 해당하는 것으로 본다)
	public boolean appliesOn(LocalDate day) {
		return date == null || date.equals(day);
	}

	// 세부 지역 글이 고른 시군구(stem 예: "제주")에 해당하나? stem 이 비어 있으면 시도 전체로 본다.
	//  - "서귀포시남부 제외" 처럼 글 끝이 「제외」면 목록은 빠지는 곳이다 → 목록에 시군구 이름(통째)이 있으면 해당 없음, 없으면 해당
	//    (제외가 "서귀포시남부" 처럼 시군구의 일부면 나머지는 해당하니 해당으로 본다)
	//  - 그 밖에는 목록에 시군구 이름이 있어야 해당. 이름이 없어도 목록이 "제주도북부 · 동남권 · 산지" 같은 권역 이름뿐이면
	//    어느 시군구인지 알 수 없으니 해당하는 것으로 본다 (특보를 놓치는 것보다 한 번 더 알려 주는 쪽이 안전하다)
	public boolean appliesToStem(String stem) {
		if (stem == null || stem.isEmpty() || detail.isEmpty()) return true;
		boolean exclude = detail.trim().endsWith("제외");
		List<String> parts = splitTop(detail);
		if (exclude) {
			for (String p : parts) {
				String name = p.replace("제외", "").trim();
				// 시군구 이름 통째가 제외 목록에 있으면 그 시군구는 빠진다 ("서귀포시" · "서귀포")
				if (name.equals(stem) || name.equals(stem + "시") || name.equals(stem + "군") || name.equals(stem + "구")) return false;
			}
			return true;
		}
		boolean region = false;
		for (String p : parts) {
			if (p.contains(stem)) return true;
			if (REGION_NAME.matcher(p).matches()) region = true;
		}
		return region;
	}

	// 시군구 이름 없이 권역만 말하는 이름: "제주도북부" · "동남권" · "산지" · "내륙" ("경주중북부" 처럼 앞에 시군구 이름이 붙은 건 아니다)
	private static final Pattern REGION_NAME = Pattern.compile("^([가-힣]*도)?([동서남북중]{1,3}[부권]|산지|평지|내륙|해안|도서)$");

	// 괄호 밖 쉼표로만 자른다: "태안, 보령(도서제외)" → ["태안", "보령(도서제외)"]
	static List<String> splitTop(String s) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		StringBuilder cur = new StringBuilder();
		for (char ch : s.toCharArray()) {
			if (ch == '(') depth++;
			if (ch == ')') depth--;
			if (ch == ',' && depth == 0) {
				parts.add(cur.toString().trim());
				cur.setLength(0);
			} else {
				cur.append(ch);
			}
		}
		parts.add(cur.toString().trim());
		return parts;
	}

	// 화면에 보일 한 줄: "한파주의보 — 경상북도(청송, 포항 …)"
	public String getText() {
		String s = kind + " — " + sido;
		if (!detail.isEmpty()) s += "(" + detail + ")";
		return s;
	}

	public String getKind() { return kind; }
	public void setKind(String kind) { this.kind = kind; }
	public boolean isPrelim() { return prelim; }
	public void setPrelim(boolean prelim) { this.prelim = prelim; }
	public String getWhen() { return when; }
	public void setWhen(String when) { this.when = when; }
	public String getSido() { return sido; }
	public void setSido(String sido) { this.sido = sido; }
	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public String getDetail() { return detail; }
	public void setDetail(String detail) { this.detail = detail; }
}
