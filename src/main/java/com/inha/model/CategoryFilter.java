package com.inha.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 분류로 거르기: 관광공사 대분류(lclsSystm1)별 칩 + 개수, 그리고 실내/실외.
// 고른 칩은 주소의 ?cat= 으로 오고, 서버가 카드 목록 · 지도 마커를 거른다 (자바스크립트가 없어도 링크로 동작).
// 하루 코스 · 날씨는 거르기 전 전체 목록으로 계산한다 (코스가 분류에 따라 달라지면 날씨 맞춤이 깨져서).
public class CategoryFilter {

	public static final String INDOOR = "indoor";
	public static final String OUTDOOR = "outdoor";
	public static final String OTHER = "etc";   // 분류 코드가 없는 곳

	// 칩 한 개
	public static class Chip {
		private final String code;     // ?cat= 에 들어갈 값 ("" 는 전체)
		private final String name;     // 화면에 보일 이름
		private final int count;
		private final boolean selected;

		Chip(String code, String name, int count, boolean selected) {
			this.code = code;
			this.name = name;
			this.count = count;
			this.selected = selected;
		}

		public String getCode() { return code; }
		public String getName() { return name; }
		public int getCount() { return count; }
		public boolean isSelected() { return selected; }
	}

	// 관광지마다 대분류 이름을 붙인다 (이름표에 없으면 코드 그대로 두지 않고 빈 글자 → "기타")
	public static void applyNames(List<TourVO> spots, Map<String, String> names) {
		for (TourVO v : spots) {
			String n = names == null ? null : names.get(v.getLclsCode());
			v.setLclsName(n == null ? "" : n);
		}
	}

	// 칩 목록: [전체] + 결과에 있는 분류(많은 순) + [기타] + [실내] [실외] (실내/실외는 둘 다 있을 때만 — 하나뿐이면 전체와 같아서)
	public static List<Chip> chips(List<TourVO> spots, String sel) {
		List<Chip> list = new ArrayList<>();
		if (spots == null || spots.isEmpty()) return list;
		String s = sel == null ? "" : sel;
		list.add(new Chip("", "전체", spots.size(), s.isEmpty()));

		// 분류별 개수 (처음 나온 순서를 지키면서 센다)
		List<String> codes = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		int other = 0, in = 0, out = 0;
		for (TourVO v : spots) {
			if (v.isIndoor()) in++; else out++;
			String c = v.getLclsCode();
			if (c.isEmpty()) {
				other++;
				continue;
			}
			int i = codes.indexOf(c);
			if (i < 0) {
				codes.add(c);
				counts.add(1);
			} else {
				counts.set(i, counts.get(i) + 1);
			}
		}
		// 개수 많은 순 (같으면 처음 나온 순 — List.sort 는 안정 정렬)
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < codes.size(); i++) order.add(i);
		order.sort((a, b) -> counts.get(b) - counts.get(a));
		for (int i : order) {
			String code = codes.get(i);
			String name = "";
			for (TourVO v : spots) {
				if (v.getLclsCode().equals(code)) {
					name = v.getLclsName();
					break;
				}
			}
			list.add(new Chip(code, name.isEmpty() ? code : name, counts.get(i), code.equals(s)));
		}
		if (other > 0) list.add(new Chip(OTHER, "기타", other, OTHER.equals(s)));
		if (in > 0 && out > 0) {
			list.add(new Chip(INDOOR, "실내", in, INDOOR.equals(s)));
			list.add(new Chip(OUTDOOR, "실외", out, OUTDOOR.equals(s)));
		}
		return list;
	}

	// 주소에서 온 ?cat= 값 검사: 칩에 있는 값이면 그대로, 아니면 ""(전체). 글자 · 숫자 8자까지만 받는다
	public static String validSel(String raw, List<Chip> chips) {
		if (raw == null || !raw.matches("[A-Za-z0-9]{1,8}")) return "";
		for (Chip c : chips) {
			if (!c.getCode().isEmpty() && c.getCode().equals(raw)) return raw;
		}
		return "";
	}

	// 이 관광지가 고른 칩에 들어가나 (빈 값 = 전체)
	public static boolean matches(TourVO v, String sel) {
		if (sel == null || sel.isEmpty()) return true;
		if (INDOOR.equals(sel)) return v.isIndoor();
		if (OUTDOOR.equals(sel)) return !v.isIndoor();
		if (OTHER.equals(sel)) return v.getLclsCode().isEmpty();
		return sel.equals(v.getLclsCode());
	}

	public static List<TourVO> filter(List<TourVO> list, String sel) {
		if (sel == null || sel.isEmpty()) return list;
		List<TourVO> out = new ArrayList<>();
		for (TourVO v : list) {
			if (matches(v, sel)) out.add(v);
		}
		return out;
	}

	// 개수 세기 (고른 칩에 들어가는 곳 수)
	public static int count(List<TourVO> list, String sel) {
		return filter(list, sel).size();
	}
}
