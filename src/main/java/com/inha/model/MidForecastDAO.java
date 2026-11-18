package com.inha.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// API ④ 기상청 중기예보 (MidFcstInfoService) — 4~10일 뒤 하늘 · 강수확률 · 기온
//  - getMidLandFcst(regId 육상, tmFc) : 오전 · 오후 날씨 문장(wf4Am …) + 강수확률(rnSt4Am …)
//  - getMidTa(regId 도시, tmFc)       : 최저 · 최고기온(taMin4 · taMax4 …)
// tmFc(발표 시각)는 하루 두 번 06시 · 18시. 번호(4~10)는 "발표 날짜 + N일" 이다.
// [확인 2026-11-18] 18시 발표에는 4일 뒤(wf4 · taMin4)가 없고 5일 뒤부터 온다 → 4일 뒤 칸은 같은 날 06시 발표로 채운다.
// [확인] 어제 18시 발표를 오늘 18시에 부르면 NO_DATA(03): 오래된 발표는 못 받는다.
public class MidForecastDAO {

	private static final String BASE = "https://apis.data.go.kr/1360000/MidFcstInfoService/";
	private static final String API_NAME = "기상청 중기예보";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter TMFC = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

	private final String serviceKey;

	public MidForecastDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	// 육상예보: 날짜별 오전 · 오후 날씨 문장과 강수확률 (날짜 순). 4~10일 뒤 중 못 받은 날은 목록에 없다
	public List<MidDayVO> land(String landRegId) throws ApiException {
		return load(landRegId, true);
	}

	// 기온예보: 날짜별 최저 · 최고기온 (날짜 순)
	public List<MidDayVO> temperature(String taRegId) throws ApiException {
		return load(taRegId, false);
	}

	// 육상 목록에 기온 목록을 날짜로 맞춰 붙인다. 기온만 있는 날도 목록에 넣는다 (taList 가 null 이어도 됨)
	public static List<MidDayVO> merge(List<MidDayVO> landList, List<MidDayVO> taList) {
		Map<LocalDate, MidDayVO> byDate = new TreeMap<>();
		if (landList != null) {
			for (MidDayVO d : landList) byDate.put(d.getDate(), d);
		}
		if (taList != null) {
			for (MidDayVO t : taList) {
				MidDayVO d = byDate.computeIfAbsent(t.getDate(), k -> {
					MidDayVO v = new MidDayVO();
					v.setDate(k);
					v.setIssued(t.getIssued());
					return v;
				});
				d.setMinTemp(t.getMinTemp());
				d.setMaxTemp(t.getMaxTemp());
			}
		}
		return new ArrayList<>(byDate.values());
	}

	// 최근 발표부터 차례로 불러 날짜별로 모은다. 먼저 받은(더 최근) 값이 이긴다.
	// 오늘 + 4 ~ + 10 일이 다 모이면 멈춘다 (보통 한 번, 18시 발표 뒤에는 두 번)
	private List<MidDayVO> load(String regId, boolean isLand) throws ApiException {
		if (regId == null || regId.isEmpty()) {
			throw new ApiException("이 지역의 중기예보 지역 코드를 몰라요.");
		}
		LocalDate today = LocalDate.now(KST);
		Map<LocalDate, MidDayVO> out = new TreeMap<>();
		ApiException firstError = null;
		int tried = 0;
		for (ZonedDateTime tmFc : candidates(ZonedDateTime.now(KST))) {
			if (tried >= 2 && !out.isEmpty()) break;
			tried++;
			try {
				JsonObject item = fetch(regId, tmFc.format(TMFC), isLand);
				if (item != null) fill(out, item, tmFc, isLand);
			} catch (ApiException e) {
				// 시간 초과 · 연결 실패면 더 오래된 발표를 또 불러 봐야 같은 기다림만 쌓인다 → 멈춘다 (자료 없음(03)만 다음 후보로 넘어간다)
				firstError = e;
				break;
			}
			if (covers(out, today)) break;
		}
		if (out.isEmpty()) {
			if (firstError != null) throw firstError;
			throw new ApiException("중기예보 자료가 아직 없어요.");
		}
		return new ArrayList<>(out.values());
	}

	// 오늘 + 4 ~ + 10 일이 다 들어 있나
	private static boolean covers(Map<LocalDate, MidDayVO> out, LocalDate today) {
		for (int n = 4; n <= 10; n++) {
			if (!out.containsKey(today.plusDays(n))) return false;
		}
		return true;
	}

	// 발표 시각 후보: 가장 최근(06 · 18시, 발표 30분 뒤부터) → 12시간 전 → 24시간 전
	static List<ZonedDateTime> candidates(ZonedDateTime now) {
		ZonedDateTime t = now.minusMinutes(30);
		ZonedDateTime latest;
		if (t.getHour() >= 18) {
			latest = t.toLocalDate().atTime(18, 0).atZone(KST);
		} else if (t.getHour() >= 6) {
			latest = t.toLocalDate().atTime(6, 0).atZone(KST);
		} else {
			latest = t.toLocalDate().minusDays(1).atTime(18, 0).atZone(KST);
		}
		List<ZonedDateTime> list = new ArrayList<>();
		list.add(latest);
		list.add(latest.minusHours(12));
		list.add(latest.minusHours(24));
		return list;
	}

	// 한 발표를 부른다. 결과 없음(NO_DATA 03)이면 null, 그 밖의 오류 코드는 ApiException
	private JsonObject fetch(String regId, String tmFc, boolean isLand) throws ApiException {
		String url = BASE + (isLand ? "getMidLandFcst" : "getMidTa") + "?serviceKey=" + serviceKey
				+ "&dataType=JSON&numOfRows=3&pageNo=1&regId=" + regId + "&tmFc=" + tmFc;
		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_MID);
		String code = ApiClient.resultCode(root);
		if (code.equals("03")) return null;
		if (!code.equals("00")) {
			throw new ApiException(API_NAME + " 오류: " + ApiClient.resultMsg(root) + " (" + code + ")");
		}
		JsonArray items = ApiClient.items(root);
		return items.size() == 0 ? null : items.get(0).getAsJsonObject();
	}

	// 한 발표의 N일 뒤 칸들을 날짜별로 채운다 (이미 있는 날짜는 건드리지 않는다)
	private static void fill(Map<LocalDate, MidDayVO> out, JsonObject item, ZonedDateTime tmFc, boolean isLand) {
		for (int n = 3; n <= 10; n++) {
			LocalDate date = tmFc.toLocalDate().plusDays(n);
			if (out.containsKey(date)) continue;
			MidDayVO d = new MidDayVO();
			d.setDate(date);
			d.setIssued(tmFc.format(TMFC));
			boolean any;
			if (isLand) {
				String am = n <= 7 ? "wf" + n + "Am" : "wf" + n;
				String pm = n <= 7 ? "wf" + n + "Pm" : "wf" + n;
				String amRn = n <= 7 ? "rnSt" + n + "Am" : "rnSt" + n;
				String pmRn = n <= 7 ? "rnSt" + n + "Pm" : "rnSt" + n;
				d.setAmWf(ApiClient.str(item, am));
				d.setPmWf(ApiClient.str(item, pm));
				d.setAmPop(intOf(item, amRn));
				d.setPmPop(intOf(item, pmRn));
				any = d.isHasLand();
			} else {
				d.setMinTemp(intOf(item, "taMin" + n));
				d.setMaxTemp(intOf(item, "taMax" + n));
				any = d.getMinTemp() != null || d.getMaxTemp() != null;
			}
			if (any) out.put(date, d);
		}
	}

	private static Integer intOf(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
		try {
			return (int) Math.round(Double.parseDouble(e.getAsString().trim()));
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
