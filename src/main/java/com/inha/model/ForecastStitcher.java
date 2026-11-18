package com.inha.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// 단기예보(오늘~글피, 시간별)와 중기예보(4~10일 뒤, 오전 · 오후)를 이어 붙여 0~10일 하루 요약(DayForecastVO)을 만든다.
//
// 이어 붙이는 규칙 (2026-11-18 실측 근거):
//  - 0~2일 뒤: 단기예보(시간별). 단기 17시 발표는 0~3일을 24시간 다 주고, 4일 뒤는 3시간 간격 8칸만 준다.
//  - 3일 뒤(글피): 단기예보가 낮 시간(09~21시) 5곳 이상 있으면 단기, 모자라면(14시 발표는 98건뿐이었음) 중기, 그것도 없으면 있는 만큼만(partial)
//  - 4~10일 뒤: 중기예보(오전 · 오후 하늘 · 강수확률 + 최저 · 최고). 18시 발표에는 4일 뒤가 없어서(MidForecastDAO 참고)
//    그 칸은 같은 날 06시 발표로 채우고, 그것도 없으면 단기예보의 3시간 간격 값(낮 5곳 이상)으로 채운다.
// 호출 수: 단기 1회(날짜별로 불러도 같은 주소라 캐시) + 중기 육상 1~2회 + 중기 기온 1~2회, 이 셋을 동시에 보낸다.
public class ForecastStitcher {

	private static final int DAYS = 11;        // 0~10일 뒤
	private static final int SHORT_TRY = 5;    // 단기예보로 시도해 볼 날: 0~4일 뒤
	private static final int MID_WAIT_SEC = 6;    // 중기예보를 기다리는 최대 시간(초)
	private static final int MIN_DAYTIME = 5;  // "그날 단기예보가 충분하다"의 기준: 09~21시 칸 수

	private final WeatherDAO weatherDao;
	private final MidForecastDAO midDao;

	public ForecastStitcher(String serviceKey) {
		this.weatherDao = new WeatherDAO(serviceKey);
		this.midDao = new MidForecastDAO(serviceKey);
	}

	// 단기 호출 결과 묶음 (날짜별 날씨 + 실패 문구)
	private static class ShortResult {
		final WeatherVO[] days = new WeatherVO[SHORT_TRY];
		String error = "";
	}

	// 좌표(단기예보 격자) + 시도 · 시군구 코드(중기 지역)로 0~10일 요약을 만든다.
	// 하나가 실패해도 나머지는 채운다 (TenDayVO 의 shortError · midError 에 문구). 컨트롤러(서블릿 스레드)에서만 부를 것:
	// 안에서 동시 호출을 보내고 기다린다 (풀 안의 작업이 이걸 부르면 안 됨).
	public TenDayVO tenDays(double lat, double lon, String sido, String sigungu) {
		String landId = RegionCodes.midLandRegId(sido, sigungu);
		String taId = RegionCodes.midTaRegId(sido, sigungu);

		CompletableFuture<ShortResult> fs = ApiClient.async(() -> loadShort(lat, lon));
		CompletableFuture<List<MidDayVO>> fl = landId == null ? null : ApiClient.async(() -> midDao.land(landId));
		CompletableFuture<List<MidDayVO>> ft = taId == null ? null : ApiClient.async(() -> midDao.temperature(taId));

		ShortResult sr;
		try {
			sr = ApiClient.await(fs);
		} catch (ApiException e) {
			sr = new ShortResult();
			sr.error = e.getMessage();
		}
		String midError = "";
		List<MidDayVO> land = null, ta = null;
		if (fl == null) {
			midError = "이 지역의 중기예보 지역 코드를 몰라요.";
		} else {
			try {
				land = ApiClient.await(fl, MID_WAIT_SEC, "기상청 중기예보"); // 중기 API 가 느려도 화면 전체가 멈추지 않게 기다리는 시간을 자른다
			} catch (ApiException e) {
				midError = e.getMessage();
			}
			try {
				if (ft != null) ta = ApiClient.await(ft, 2, "기상청 중기 기온예보"); // 육상과 같이 시작했으니 보통 이미 끝나 있다
			} catch (ApiException e) {
				if (midError.isEmpty()) midError = e.getMessage(); // 기온만 실패해도 안내 (육상 값은 그대로 쓴다)
			}
		}
		TenDayVO ten = stitch(lat, lon, sr.days, sr.error, MidForecastDAO.merge(land, ta), midError);
		ten.setMidTaCity(RegionCodes.midTaCity(sido, sigungu));
		return ten;
	}

	// 단기예보를 0~4일 뒤까지 읽는다. 같은 주소라 첫 호출만 나가고 나머지는 캐시.
	// "그날 예보가 아직 나오지 않았어요" 는 그 날만 비우고 계속, 그 밖의 오류(연결 · 자료 없음)는 멈추고 문구를 남긴다
	private ShortResult loadShort(double lat, double lon) {
		ShortResult r = new ShortResult();
		for (int d = 0; d < SHORT_TRY; d++) {
			try {
				r.days[d] = weatherDao.forecast(lat, lon, d);
			} catch (ApiException e) {
				if (e.getMessage().contains("그날 예보")) continue;
				r.error = e.getMessage();
				break;
			}
		}
		return r;
	}

	// 단기 · 중기 값을 받아 11칸으로 이어 붙인다 (네트워크 없이 시험할 수 있게 따로 뺐다).
	// shorts: 0~4일 뒤 단기 날씨(없는 날은 null), mid: 날짜가 들어 있는 중기 목록(null 가능)
	public static TenDayVO stitch(double lat, double lon, WeatherVO[] shorts, String shortError,
			List<MidDayVO> mid, String midError) {
		TenDayVO out = new TenDayVO();
		out.setShortError(shortError == null ? "" : shortError);
		out.setMidError(midError == null ? "" : midError);

		Map<LocalDate, MidDayVO> midByDate = new HashMap<>();
		if (mid != null) {
			for (MidDayVO m : mid) {
				midByDate.put(m.getDate(), m);
				if (out.getMidIssued().isEmpty() || m.getIssued().compareTo(out.getMidIssued()) > 0) {
					out.setMidIssued(m.getIssued());
				}
			}
		}

		for (int d = 0; d < DAYS; d++) {
			WeatherVO sw = (shorts != null && d < shorts.length) ? shorts[d] : null;
			MidDayVO md = midByDate.get(Area.dayOf(d));
			boolean midOk = md != null && md.isHasLand();
			boolean shortEnough = sw != null && daytimeCount(sw) >= MIN_DAYTIME;

			DayForecastVO day;
			if (d <= 2 && sw != null) {
				day = fromShort(d, sw, false);
			} else if (d == 3 && shortEnough) {
				day = fromShort(d, sw, false);
			} else if (d >= 3 && midOk) {
				day = fromMid(d, md, lat, lon);
			} else if (d == 4 && shortEnough) {
				day = fromShort(d, sw, false); // 18시 중기 발표에 4일 뒤가 없을 때의 대체
			} else if (d == 3 && sw != null) {
				day = fromShort(d, sw, true); // 일부 시간만
			} else {
				day = none(d, out.getShortError(), out.getMidError());
			}
			out.getDays().add(day);
		}
		return out;
	}

	// 09~21시 칸 수
	private static int daytimeCount(WeatherVO w) {
		int c = 0;
		for (HourVO h : w.getHours()) {
			int hour = h.getHourOfDay();
			if (hour >= 9 && hour <= 21) c++;
		}
		return c;
	}

	private static DayForecastVO base(int d, String source) {
		DayForecastVO v = new DayForecastVO();
		v.setDayOffset(d);
		v.setDate(Area.dayOf(d));
		v.setDateLabel(Area.dateLabel(d));
		v.setSource(source);
		return v;
	}

	private static DayForecastVO none(int d, String shortError, String midError) {
		DayForecastVO v = base(d, DayForecastVO.NONE);
		String why = d <= 3 ? shortError : midError;
		v.setNote(why == null || why.isEmpty() ? "이날 예보는 아직 나오지 않았어요." : why);
		return v;
	}

	// 단기예보 하루 → 요약 (오전 = 06~11시, 오후 = 12~21시 칸에서 뽑는다)
	private static DayForecastVO fromShort(int d, WeatherVO w, boolean partial) {
		DayForecastVO v = base(d, DayForecastVO.SHORT);
		v.setMinTemp(w.getMinTemp());
		v.setMaxTemp(w.getMaxTemp());
		v.setMaxPop(w.getMaxPop());
		v.setSky(w.getSky());
		v.setIcon(w.getIcon());
		v.setRainType(w.getRainType());
		v.setRainy(w.isRainy());
		v.setHours(w.getHours());
		v.setHourly(!w.getHours().isEmpty());
		v.setWeather(w);

		List<HourVO> am = new ArrayList<>(), pm = new ArrayList<>();
		for (HourVO h : w.getHours()) {
			int hour = h.getHourOfDay();
			if (hour >= 6 && hour < 12) am.add(h);
			if (hour >= 12 && hour <= 21) pm.add(h);
		}
		String[] a = representative(am), p = representative(pm);
		if (a != null) {
			v.setAmSky(a[0]);
			v.setAmIcon(a[1]);
			v.setAmPop(maxPop(am));
		}
		if (p != null) {
			v.setPmSky(p[0]);
			v.setPmIcon(p[1]);
			v.setPmPop(maxPop(pm));
		}
		boolean threeHour = d >= 4; // 4일 뒤 단기예보는 3시간 간격
		v.setPartial(partial || w.isPartial());
		if (partial) {
			v.setNote("일부 시간만 예보가 나와 있어요.");
		} else if (w.isPartial()) {
			v.setNote("오늘이라 지난 시간은 빠졌어요.");
		} else if (threeHour) {
			v.setNote("단기예보라 3시간 간격으로만 나와요.");
		}
		return v;
	}

	// 칸들의 대표 하늘 {하늘, 아이콘}: 비 · 눈 칸이 있으면 그것, 없으면 가장 많은 하늘. 칸이 없으면 null
	private static String[] representative(List<HourVO> hs) {
		if (hs.isEmpty()) return null;
		for (HourVO h : hs) {
			if (h.isRainSky()) return new String[] { h.getSky(), h.getIcon() };
		}
		Map<String, Integer> count = new HashMap<>();
		HourVO best = hs.get(0);
		for (HourVO h : hs) {
			int c = count.merge(h.getSky(), 1, Integer::sum);
			if (c > count.get(best.getSky())) best = h;
		}
		return new String[] { best.getSky(), best.getIcon() };
	}

	private static int maxPop(List<HourVO> hs) {
		int m = 0;
		for (HourVO h : hs) m = Math.max(m, h.getPop());
		return m;
	}

	// 중기예보 하루 → 요약 (WeatherVO 도 같이 만들어 1판 화면 · 추천 로직에 그대로 넣을 수 있게 한다)
	private static DayForecastVO fromMid(int d, MidDayVO m, double lat, double lon) {
		DayForecastVO v = base(d, DayForecastVO.MID);
		String[] am = midSky(m.getAmWf()), pm = midSky(m.getPmWf());
		if (am == null) am = pm;
		if (pm == null) pm = am;

		v.setAmSky(am[0]);
		v.setAmIcon(am[1]);
		v.setPmSky(pm[0]);
		v.setPmIcon(pm[1]);
		v.setAmPop(m.getAmPop() == null ? -1 : m.getAmPop());
		v.setPmPop(m.getPmPop() == null ? -1 : m.getPmPop());
		v.setMinTemp(m.getMinTemp());
		v.setMaxTemp(m.getMaxTemp());
		int maxPop = Math.max(Math.max(v.getAmPop(), v.getPmPop()), 0);
		v.setMaxPop(maxPop);

		// 대표: 비 · 눈이 낀 쪽이 있으면 더 센 쪽, 없으면 더 흐린 쪽
		String[] pick = worse(am, pm);
		boolean rainy = !pick[2].isEmpty() || maxPop >= 60;
		v.setRainType(pick[2]);
		v.setRainy(rainy);
		if (rainy && pick[2].isEmpty()) { // 확률만 높으면 "비"로 표시 (단기예보 요약과 같은 규칙)
			v.setSky("비");
			v.setIcon(WeatherDAO.ptyIcon(1));
			v.setRainType("비");
		} else {
			v.setSky(pick[0]);
			v.setIcon(pick[1]);
		}
		v.setHourly(false);
		v.setNote("중기예보라 시간별 예보는 없어요. 오전 · 오후만 나와요.");

		WeatherVO w = new WeatherVO();
		w.setDateLabel(v.getDateLabel());
		w.setLat(lat);
		w.setLon(lon);
		int[] grid = GridConverter.toGrid(lat, lon);
		w.setNx(grid[0]);
		w.setNy(grid[1]);
		String issued = m.getIssued();
		w.setBaseDate(issued.length() >= 8 ? issued.substring(0, 8) : "");
		w.setBaseTime(issued.length() >= 12 ? issued.substring(8, 12) : "");
		w.setMinTemp(v.getMinTemp());
		w.setMaxTemp(v.getMaxTemp());
		w.setMaxPop(v.getMaxPop());
		w.setSky(v.getSky());
		w.setIcon(v.getIcon());
		w.setRainType(v.getRainType());
		w.setRainy(v.isRainy());
		w.setRainTime("");
		w.setPartial(false);
		v.setWeather(w);
		return v;
	}

	// 중기 날씨 문장 → { 하늘, 아이콘, 비·눈 종류("" 가능) }. 문장이 비면 null
	// 문장 예: 맑음 · 구름많음 · 흐림 · 구름많고 비 · 흐리고 비/눈 · 구름많고 소나기 · 흐리고 눈
	static String[] midSky(String wf) {
		if (wf == null || wf.isEmpty()) return null;
		int pty = 0;
		if (wf.contains("비/눈")) pty = 2;
		else if (wf.contains("소나기")) pty = 4;
		else if (wf.contains("눈")) pty = 3;
		else if (wf.contains("비")) pty = 1;
		if (pty > 0) {
			String rain = WeatherDAO.ptyText(pty);
			return new String[] { rain, WeatherDAO.ptyIcon(pty), rain };
		}
		int sky = wf.startsWith("흐") ? 4 : wf.startsWith("구름") ? 3 : 1;
		return new String[] { WeatherDAO.skyText(sky), WeatherDAO.skyIcon(sky), "" };
	}

	// 오전 · 오후 중 대표로 쓸 쪽: 비 · 눈이 있으면 더 센 쪽, 둘 다 없으면 더 흐린 쪽
	private static String[] worse(String[] a, String[] b) {
		boolean ra = !a[2].isEmpty(), rb = !b[2].isEmpty();
		if (ra && rb) return rainRank(a[2]) >= rainRank(b[2]) ? a : b;
		if (ra) return a;
		if (rb) return b;
		return skyRank(a[0]) >= skyRank(b[0]) ? a : b;
	}

	private static int rainRank(String rain) {
		switch (rain) {
		case "비/눈": return WeatherDAO.severity(2);
		case "눈": return WeatherDAO.severity(3);
		case "비": return WeatherDAO.severity(1);
		default: return WeatherDAO.severity(4);
		}
	}

	private static int skyRank(String sky) {
		switch (sky) {
		case "흐림": return 2;
		case "구름많음": return 1;
		default: return 0;
		}
	}
}
