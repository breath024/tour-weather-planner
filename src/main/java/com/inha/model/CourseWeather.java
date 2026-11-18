package com.inha.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// 내 코스에 담은 곳마다 그날 날씨 한 줄 (달력 .ics 메모용). 컨트롤러(서블릿 스레드)에서만 부른다
//  같은 격자(nx,ny)는 한 번만 조회 · 예보는 ApiClient 캐시를 쓰므로 방금 /course 화면을 봤다면 외부 호출 0회
//  3일 뒤부터는 단기 + 중기를 이어 붙인 예보(격자 + 시도 + 시군구마다 한 번)
public class CourseWeather {

	// contentId → "맑음 · 1° ~ 8° · 강수확률 0%" (실외인데 비 예보면 뒤에 경고). 못 구한 곳은 map 에 없다
	public static Map<String, String> lines(List<CourseItem> items, String apiKey, int day) {
		Map<String, String> result = new HashMap<>();
		if (apiKey == null || items.isEmpty()) return result;
		WeatherDAO dao = new WeatherDAO(apiKey);
		boolean longDay = day >= 3;
		Map<String, CompletableFuture<WeatherVO>> byGrid = new LinkedHashMap<>();
		Map<String, CompletableFuture<DayForecastVO>> byGridLong = new LinkedHashMap<>();
		for (CourseItem it : items) {
			if (!it.isHasLocation()) continue;
			int[] g = GridConverter.toGrid(it.getLat(), it.getLon());
			String gk = g[0] + "," + g[1];
			if (longDay) {
				String lk = gk + "|" + it.getSidoCode() + "|" + it.getSigunguCode();
				if (!byGridLong.containsKey(lk)) {
					byGridLong.put(lk, ApiClient.asyncOuter(() ->
							new ForecastStitcher(apiKey).tenDays(it.getLat(), it.getLon(), it.getSidoCode(), it.getSigunguCode()).getDay(day)));
				}
			} else if (!byGrid.containsKey(gk)) {
				byGrid.put(gk, ApiClient.async(() -> dao.forecast(it.getLat(), it.getLon(), day)));
			}
		}
		for (CourseItem it : items) {
			if (!it.isHasLocation()) continue;
			int[] g = GridConverter.toGrid(it.getLat(), it.getLon());
			String gk = g[0] + "," + g[1];
			try {
				WeatherVO w;
				if (longDay) {
					w = byGridLong.get(gk + "|" + it.getSidoCode() + "|" + it.getSigunguCode()).join().getWeather();
				} else {
					w = ApiClient.await(byGrid.get(gk));
				}
				if (w != null) result.put(it.getContentId(), summary(w, it.isIndoor()));
			} catch (Exception e) {
				// 이 곳 날씨는 비워 둔다 (달력 파일은 날씨 없이도 만든다)
			}
		}
		return result;
	}

	// 날씨 한 줄 요약
	static String summary(WeatherVO w, boolean indoor) {
		StringBuilder sb = new StringBuilder();
		sb.append(w.getSky()).append(" · ").append(w.getTempRange()).append(" · 강수확률 ").append(w.getMaxPop()).append('%');
		if (!indoor && w.isRainy()) sb.append(" · 실외인데 ").append(w.getRainType().isEmpty() ? "비" : w.getRainType()).append(" 예보가 있어요");
		return sb.toString();
	}
}
