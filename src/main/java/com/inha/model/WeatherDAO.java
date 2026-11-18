package com.inha.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// API ② 기상청 단기예보 조회서비스 (VilageFcstInfoService_2.0 / getVilageFcst)
public class WeatherDAO {

	private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
	private static final String API_NAME = "기상청 단기예보";
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
	private final String serviceKey;

	public WeatherDAO(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	// 위도·경도와 날짜(0 오늘, 1 내일, 2 모레)로 하루 날씨를 조회
	public WeatherVO forecast(double lat, double lon, int dayOffset) throws ApiException {
		// ① 관광지 좌표 → 기상청 격자
		int[] grid = GridConverter.toGrid(lat, lon);

		// ② 가장 최근 발표 시각 고르기
		//    단기예보는 02·05·08·11·14·17·20·23시에 발표되고 10분 뒤부터 조회된다 → 15분 여유
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(15);
		int[] baseHours = { 23, 20, 17, 14, 11, 8, 5, 2 };
		LocalDate baseDay = now.toLocalDate();
		int baseHour = -1;
		for (int h : baseHours) {
			if (now.getHour() >= h) {
				baseHour = h;
				break;
			}
		}
		if (baseHour == -1) { // 0시~2시 15분 사이 → 전날 23시 발표
			baseHour = 23;
			baseDay = baseDay.minusDays(1);
		}
		// 오늘 23시 발표는 다음 날 00시부터의 예보라서 오늘 날씨가 없다 → 23:15 이후 '오늘'은 20시 발표(21~23시 포함)를 쓴다
		if (dayOffset == 0 && baseHour == 23 && baseDay.equals(ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toLocalDate())) {
			baseHour = 20;
		}
		String baseDate = baseDay.format(YMD);
		String baseTime = String.format("%02d00", baseHour);

		String url = URL + "?serviceKey=" + serviceKey
				+ "&pageNo=1&numOfRows=1500&dataType=JSON"
				+ "&base_date=" + baseDate + "&base_time=" + baseTime
				+ "&nx=" + grid[0] + "&ny=" + grid[1];

		JsonObject root = ApiClient.getJson(url, API_NAME, ApiClient.TTL_LIST); // 같은 발표 시각 + 같은 격자면 캐시 재사용
		String code = ApiClient.resultCode(root);
		if (code.equals("03")) {
			throw new ApiException("기상청 예보 자료가 아직 없어요. 잠시 뒤 다시 시도해 주세요.");
		}
		if (!code.equals("00")) {
			throw new ApiException(API_NAME + " 오류: " + ApiClient.resultMsg(root) + " (" + code + ")");
		}

		// ③ 고른 날짜의 항목만 시각별로 모으기  { "0900" : { "TMP":"12", "POP":"30", ... } }
		String target = Area.dayOf(dayOffset).format(YMD);
		Map<String, Map<String, String>> byTime = new TreeMap<>();
		String tmn = "", tmx = "";
		JsonArray items = ApiClient.items(root);
		for (int i = 0; i < items.size(); i++) {
			JsonObject it = items.get(i).getAsJsonObject();
			if (!target.equals(ApiClient.str(it, "fcstDate"))) continue;
			String cat = ApiClient.str(it, "category");
			String val = ApiClient.str(it, "fcstValue");
			if (cat.equals("TMN")) tmn = val;
			if (cat.equals("TMX")) tmx = val;
			byTime.computeIfAbsent(ApiClient.str(it, "fcstTime"), k -> new HashMap<>()).put(cat, val);
		}
		if (byTime.isEmpty()) {
			throw new ApiException("그날 예보가 아직 나오지 않았어요.");
		}

		WeatherVO w = new WeatherVO();
		w.setDateLabel(Area.dateLabel(dayOffset));
		w.setLat(lat);
		w.setLon(lon);
		w.setNx(grid[0]);
		w.setNy(grid[1]);
		w.setBaseDate(baseDate);
		w.setBaseTime(baseTime);

		// ④ 하루 요약 만들기 (여행 시간대 09~21시 기준)
		Integer min = null, max = null;
		int maxPop = 0;
		int worstPty = 0;
		String rainTime = "";
		int[] skyCount = new int[5];
		List<HourVO> hours = new ArrayList<>();
		boolean anyDaytime = false;
		for (String t : byTime.keySet()) {
			int hour = Integer.parseInt(t.substring(0, 2));
			if (hour >= 9 && hour <= 21) anyDaytime = true;
		}
		String firstTime = "";

		for (Map.Entry<String, Map<String, String>> e : byTime.entrySet()) {
			String t = e.getKey();
			Map<String, String> v = e.getValue();
			int hour = Integer.parseInt(t.substring(0, 2));
			if (firstTime.isEmpty()) firstTime = t;

			Integer temp = toInt(v.get("TMP"));
			if (temp != null) {
				if (min == null || temp < min) min = temp;
				if (max == null || temp > max) max = temp;
			}
			int pop = toInt(v.get("POP")) == null ? 0 : toInt(v.get("POP"));
			int pty = toInt(v.get("PTY")) == null ? 0 : toInt(v.get("PTY"));
			int sky = toInt(v.get("SKY")) == null ? 1 : toInt(v.get("SKY"));

			boolean daytime = !anyDaytime || (hour >= 9 && hour <= 21);
			if (daytime) {
				if (pop > maxPop) maxPop = pop;
				if (pty > 0 && severity(pty) > severity(worstPty)) worstPty = pty;
				if ((pty > 0 || pop >= 60) && rainTime.isEmpty()) rainTime = hour + "시";
				if (sky >= 1 && sky <= 4) skyCount[sky]++;
			}

			if (hour >= 6) { // 화면에는 06시부터
				HourVO h = new HourVO();
				h.setTime(String.format("%02d시", hour));
				h.setTemp(temp == null ? "-" : String.valueOf(temp));
				h.setPop(pop);
				h.setSky(pty > 0 ? ptyText(pty) : skyText(sky));
				h.setIcon(pty > 0 ? ptyIcon(pty) : skyIcon(sky));
				hours.add(h);
			}
		}

		// 기상청이 준 최저·최고(TMN/TMX)가 있으면 그 값을 우선
		Integer tmnVal = toInt(tmn), tmxVal = toInt(tmx);
		w.setMinTemp(tmnVal != null ? tmnVal : min);
		w.setMaxTemp(tmxVal != null ? tmxVal : max);
		w.setMaxPop(maxPop);
		w.setHours(hours);
		w.setPartial(dayOffset == 0 && Integer.parseInt(firstTime.substring(0, 2)) > 6);

		boolean rainy = worstPty > 0 || maxPop >= 60;
		w.setRainy(rainy);
		w.setRainTime(rainTime);
		if (rainy) {
			int shown = worstPty > 0 ? worstPty : 1; // 확률만 높으면 "비"로 표시
			w.setRainType(ptyText(shown));
			w.setSky(ptyText(shown));
			w.setIcon(ptyIcon(shown));
		} else {
			int best = 1;
			for (int s : new int[] { 1, 3, 4 }) {
				if (skyCount[s] > skyCount[best]) best = s;
			}
			w.setRainType("");
			w.setSky(skyText(best));
			w.setIcon(skyIcon(best));
		}
		return w;
	}

	private static Integer toInt(String s) {
		if (s == null || s.isEmpty()) return null;
		try {
			return (int) Math.round(Double.parseDouble(s));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// 강수형태(PTY): 0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기
	static int severity(int pty) {
		switch (pty) {
		case 2: return 4;
		case 3: return 3;
		case 1: return 2;
		case 4: return 1;
		default: return 0;
		}
	}

	static String ptyText(int pty) {
		switch (pty) {
		case 1: return "비";
		case 2: return "비/눈";
		case 3: return "눈";
		case 4: return "소나기";
		default: return "";
		}
	}

	// 화면이 그리는 아이콘 이름표(head.jspf 의 w-... 그림): rain · sleet · snow · shower
	static String ptyIcon(int pty) {
		switch (pty) {
		case 3: return "snow";
		case 2: return "sleet";
		case 4: return "shower";
		default: return "rain";
		}
	}

	// 하늘상태(SKY): 1 맑음, 3 구름많음, 4 흐림
	static String skyText(int sky) {
		switch (sky) {
		case 3: return "구름많음";
		case 4: return "흐림";
		default: return "맑음";
		}
	}

	// 하늘 아이콘 이름표: sun · cloud-sun · overcast
	static String skyIcon(int sky) {
		switch (sky) {
		case 3: return "cloud-sun";
		case 4: return "overcast";
		default: return "sun";
		}
	}
}
