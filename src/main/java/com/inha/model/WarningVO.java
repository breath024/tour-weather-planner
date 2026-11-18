package com.inha.model;

import java.util.ArrayList;
import java.util.List;

// 기상특보 현황 전체 (전국 한 덩어리). 시도별로 거르는 건 forSido
public class WarningVO {
	private String tmFc = "";   // 발표 시각 "202611181600"
	private String tmEf = "";   // 발효 시각 "202611182100" (없으면 "")
	private final List<WarningItem> current = new ArrayList<>(); // 현재 발효 중인 특보 (한 줄 = 종류 × 시도)
	private final List<WarningItem> prelim = new ArrayList<>();  // 예비특보

	// 시도 이름들(RegionCodes.warnNames)에 해당하는 특보 (현재 특보 먼저, 그다음 예비특보)
	public List<WarningItem> forSido(String[] names) {
		List<WarningItem> list = new ArrayList<>();
		for (WarningItem w : current) {
			if (matches(w, names)) list.add(w);
		}
		for (WarningItem w : prelim) {
			if (matches(w, names)) list.add(w);
		}
		return list;
	}

	// 현재 특보만
	public List<WarningItem> currentForSido(String[] names) {
		List<WarningItem> list = new ArrayList<>();
		for (WarningItem w : current) {
			if (matches(w, names)) list.add(w);
		}
		return list;
	}

	// 예비특보만
	public List<WarningItem> prelimForSido(String[] names) {
		List<WarningItem> list = new ArrayList<>();
		for (WarningItem w : prelim) {
			if (matches(w, names)) list.add(w);
		}
		return list;
	}

	private static boolean matches(WarningItem w, String[] names) {
		for (String n : names) {
			if (n.equals(w.getSido())) return true;
		}
		return false;
	}

	public String getTmFc() { return tmFc; }
	public void setTmFc(String tmFc) { this.tmFc = tmFc; }
	public String getTmEf() { return tmEf; }
	public void setTmEf(String tmEf) { this.tmEf = tmEf; }
	public List<WarningItem> getCurrent() { return current; }
	public List<WarningItem> getPrelim() { return prelim; }
}
