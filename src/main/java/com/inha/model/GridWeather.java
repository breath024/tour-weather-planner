package com.inha.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// 관광지마다 "자기 자리" 날씨를 붙인다 — 융합 연결 고리를 관광지 하나하나에 적용
//   관광지 좌표 → 기상청 격자(nx,ny) → 같은 격자끼리 묶어서 격자마다 한 번만 동시에 조회
//   격자는 최대 16개까지. 넘치거나 실패한 곳은 가장 가까운 조회된 격자 값을 빌려 쓰고 "대략"(approx)으로 표시한다
public class GridWeather {

	public static final int MAX_GRIDS = 16;

	private WeatherVO center;            // 가운데 지점 날씨 (요약 카드용). 실패하면 null
	private ApiException centerError;    // 가운데 날씨 실패 이유
	private int gridCount;               // 실제로 조회한(부른) 격자 수
	private int approxCount;             // 가까운 격자 값을 빌려 쓴 관광지 수
	private int failedGrids;             // 조회에 실패한 격자 수

	// 묶음에서 한 격자 정보: 대표 좌표와 그 격자의 관광지 수
	private static class Cell {
		double lat, lon;
		int count;
		Cell(double lat, double lon) {
			this.lat = lat;
			this.lon = lon;
		}
	}

	private static String key(double lat, double lon) {
		int[] g = GridConverter.toGrid(lat, lon);
		return g[0] + "," + g[1];
	}

	// spots 의 각 곳에 weather · weatherApprox 를 채운다. centerPoint = {위도, 경도} (요약 카드 날씨 지점)
	public static GridWeather load(List<TourVO> spots, double[] centerPoint, WeatherDAO dao, int day) {
		return load(spots, centerPoint, dao, day, MAX_GRIDS);
	}

	// maxGrids: 한 번에 조회할 격자 수 상한 (축제 행사처럼 곁가지는 작게 줘서 기상청 호출을 아낀다)
	public static GridWeather load(List<TourVO> spots, double[] centerPoint, WeatherDAO dao, int day, int maxGrids) {
		GridWeather result = new GridWeather();

		// ① 격자별로 묶기 (가운데 지점의 격자가 맨 앞)
		Map<String, Cell> cells = new LinkedHashMap<>();
		String centerKey = key(centerPoint[0], centerPoint[1]);
		cells.put(centerKey, new Cell(centerPoint[0], centerPoint[1]));
		Map<String, Cell> spotCells = new LinkedHashMap<>();
		for (TourVO s : spots) {
			if (!s.isHasLocation()) continue;
			String k = key(s.getLat(), s.getLon());
			Cell c = spotCells.computeIfAbsent(k, x -> new Cell(s.getLat(), s.getLon()));
			c.count++;
		}
		// 관광지가 많은 격자부터 16개까지만 조회한다
		List<Map.Entry<String, Cell>> sorted = new ArrayList<>(spotCells.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));
		for (Map.Entry<String, Cell> e : sorted) {
			if (cells.size() >= maxGrids) break;
			cells.putIfAbsent(e.getKey(), e.getValue());
		}

		// ② 격자마다 한 번씩 동시에 조회
		Map<String, CompletableFuture<WeatherVO>> futures = new LinkedHashMap<>();
		for (Map.Entry<String, Cell> e : cells.entrySet()) {
			Cell c = e.getValue();
			futures.put(e.getKey(), ApiClient.async(() -> dao.forecast(c.lat, c.lon, day)));
		}
		result.gridCount = futures.size();

		Map<String, WeatherVO> ok = new LinkedHashMap<>();
		for (Map.Entry<String, CompletableFuture<WeatherVO>> e : futures.entrySet()) {
			try {
				ok.put(e.getKey(), ApiClient.await(e.getValue()));
			} catch (ApiException ex) {
				result.failedGrids++;
				if (e.getKey().equals(centerKey)) result.centerError = ex;
			}
		}
		result.center = ok.get(centerKey);

		// ③ 관광지에 붙이기: 자기 격자 날씨가 있으면 그대로, 없으면 가장 가까운 조회된 격자 값(대략)
		for (TourVO s : spots) {
			if (!s.isHasLocation()) {
				s.setWeather(result.center);          // 좌표가 없으면 가운데 날씨로 (대략)
				s.setWeatherApprox(result.center != null);
				continue;
			}
			WeatherVO w = ok.get(key(s.getLat(), s.getLon()));
			if (w != null) {
				s.setWeather(w);
				s.setWeatherApprox(false);
				continue;
			}
			String nearest = null;
			double best = Double.MAX_VALUE;
			for (String k : ok.keySet()) {
				Cell c = cells.get(k);
				double d = Geo.distanceKm(s.getLat(), s.getLon(), c.lat, c.lon);
				if (d < best) {
					best = d;
					nearest = k;
				}
			}
			s.setWeather(nearest == null ? null : ok.get(nearest));
			s.setWeatherApprox(nearest != null);
			if (nearest != null) result.approxCount++;
		}
		return result;
	}

	public WeatherVO getCenter() { return center; }
	public ApiException getCenterError() { return centerError; }
	public int getGridCount() { return gridCount; }
	public int getApproxCount() { return approxCount; }
	public int getFailedGrids() { return failedGrids; }
}
