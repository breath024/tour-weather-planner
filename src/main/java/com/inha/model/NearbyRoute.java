package com.inha.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// 추천 동선: 내 위치(또는 지도에서 누른 곳)에서 출발해 주변 관광지를 몇 곳 골라 도는 순서를 짠다.
//  - 지금 날씨에 맞는 곳(fit)을 먼저 고른다. 덜 맞는 곳은 1.8배 멀게 친다.
//  - 해가 진 뒤에 도착하는 실외는 2.5배 멀게 쳐서 실내를 먼저 고르게 한다.
//  - 고른 곳에서 다시 가장 가까운 곳으로 잇는다(가까운 이웃 순서).
//  - 이동 시간은 길 찾기 API 없이 직선거리로 어림한다(Geo.moveMinutes). 머무는 시간은 실내 60분 · 실외 50분.
// API 를 더 부르지 않는다. 이미 받은 주변 관광지 목록만 쓴다.
public class NearbyRoute {
	public static final int MAX_STOPS = 4;
	static final int STAY_IN = 60;
	static final int STAY_OUT = 50;
	static final double NOFIT_PENALTY = 1.8;
	static final double NIGHT_OUT_PENALTY = 2.5;
	static final int OPEN_MIN = 9 * 60;      // 아침 9시 전이면 9시에 출발하는 것으로 짠다
	static final int LATE_MIN = 19 * 60;     // 저녁 7시 넘어서 짜면 문 닫은 곳이 있을 수 있다고 알린다
	static final int FUTURE_START = 10 * 60; // 내일 뒤의 날은 오전 10시 출발

	public static class Stop {
		private final TourVO spot;
		private final int no;
		private double legKm;           // 앞 지점(처음은 출발점)에서 직선거리 · 길을 받으면 길 거리
		private int legMin;             // 이동 시간 (어림 또는 길 찾기)
		private boolean walk;
		private RoadRoute.Leg road;     // 앞 곳에서 오는 실제 길 (첫 곳 · 못 받았으면 null)
		private int arrive;             // 하루 0시부터 분
		private int leave;
		private boolean afterSunset;

		Stop(TourVO spot, int no, double legKm, int arrive, int leave, boolean afterSunset) {
			this.spot = spot;
			this.no = no;
			this.legKm = Math.round(legKm * 10) / 10.0;
			this.legMin = Geo.moveMinutes(legKm);
			this.walk = Geo.walkable(legKm);
			this.arrive = arrive;
			this.leave = leave;
			this.afterSunset = afterSunset;
		}

		public TourVO getSpot() { return spot; }
		public int getNo() { return no; }
		public double getLegKm() { return legKm; }
		public int getLegMin() { return legMin; }
		public boolean isWalk() { return walk; }
		public boolean isRoad() { return road != null; }
		public String getLegText() { return road != null ? road.getText() : Geo.moveText(legKm); }
		public String getLegDist() { return road != null ? "길 따라 " + road.getDistText() : String.format(java.util.Locale.ROOT, "직선 %.1fkm", legKm); }
		public String getArrive() { return clock(arrive); }
		public String getLeave() { return clock(leave); }
		public boolean isAfterSunset() { return afterSunset; }
	}

	public static class Result {
		private final List<Stop> stops = new ArrayList<>();
		private double totalKm;
		private int moveMin;
		private int startMin;
		private int endMin;
		private int sunsetMin = -1;
		private String note = "";
		private RoadRoute.Result road;

		public RoadRoute.Result getRoad() { return road; }

		public List<Stop> getStops() { return stops; }
		public boolean isAvailable() { return stops.size() >= 2; }
		public double getTotalKm() { return Math.round(totalKm * 10) / 10.0; }
		public int getMoveMin() { return moveMin; }
		public String getStart() { return clock(startMin); }
		public String getEnd() { return clock(endMin); }
		public String getNote() { return note; }
	}

	// spots: 거리를 아는 주변 관광지(fit · indoor 가 이미 정해진 것). sunsetMin: 일몰 시각(분), 모르면 -1
	public static Result plan(List<TourVO> spots, double lat, double lon, int day, LocalTime now, int sunsetMin) {
		Result r = new Result();
		List<TourVO> pool = new ArrayList<>();
		for (TourVO s : spots) if (s.isHasLocation()) pool.add(s);
		if (pool.size() < 2) {
			r.note = "주변에 관광지가 적어서 동선을 짜지 못했어요.";
			return r;
		}

		int start;
		if (day == 0) {
			int nowMin = now.getHour() * 60 + now.getMinute();
			start = Math.max(OPEN_MIN, (nowMin + 9) / 10 * 10); // 10분 단위로 올림
			if (nowMin >= LATE_MIN) r.note = "저녁 늦은 시간이라 문을 닫은 곳이 있을 수 있어요. 운영 시간은 상세에서 확인해 주세요.";
		} else {
			start = FUTURE_START;
		}
		r.startMin = start;
		r.sunsetMin = sunsetMin;

		double curLat = lat, curLon = lon;
		int t = start;
		int want = Math.min(MAX_STOPS, pool.size());
		while (r.stops.size() < want && !pool.isEmpty()) {
			TourVO best = null;
			double bestCost = Double.MAX_VALUE, bestKm = 0;
			for (TourVO s : pool) {
				double km = Geo.distanceKm(curLat, curLon, s.getLat(), s.getLon());
				int arrive = t + Geo.moveMinutes(km);
				double cost = Math.max(km, 0.05);
				if (!s.isFit()) cost *= NOFIT_PENALTY;
				if (sunsetMin > 0 && !s.isIndoor() && arrive >= sunsetMin) cost *= NIGHT_OUT_PENALTY;
				if (cost < bestCost) { bestCost = cost; best = s; bestKm = km; }
			}
			pool.remove(best);
			int move = Geo.moveMinutes(bestKm);
			int arrive = t + move;
			int leave = arrive + (best.isIndoor() ? STAY_IN : STAY_OUT);
			boolean dark = sunsetMin > 0 && !best.isIndoor() && arrive >= sunsetMin;
			r.stops.add(new Stop(best, r.stops.size() + 1, bestKm, arrive, leave, dark));
			r.totalKm += bestKm;
			r.moveMin += move;
			curLat = best.getLat();
			curLon = best.getLon();
			t = leave;
		}
		r.endMin = t;
		boolean anyNoFit = false;
		for (Stop s : r.stops) if (!s.getSpot().isFit()) anyNoFit = true;
		if (anyNoFit && r.note.isEmpty()) r.note = "날씨에 잘 맞는 곳이 모자라서 덜 맞는 곳도 넣었어요.";
		return r;
	}

	// 둘째 곳부터 구간을 실제 길로 바꾸고 도착 · 떠나는 시각을 다시 센다 (첫 구간 = 출발점에서는 위치를 보내지 않아서 어림 그대로)
	public static void applyRoad(Result r, RoadRoute.Result road) {
		if (road == null || road.getLegs().size() != r.stops.size() - 1) return;
		r.road = road;
		for (int i = 1; i < r.stops.size(); i++) {
			Stop s = r.stops.get(i);
			RoadRoute.Leg l = road.getLegs().get(i - 1);
			s.road = l;
			s.legKm = l.getKm();
			s.legMin = l.getMinutes();
			s.walk = l.isWalk();
		}
		int t = r.startMin;
		double km = 0;
		int move = 0;
		for (Stop s : r.stops) {
			s.arrive = t + s.legMin;
			s.leave = s.arrive + (s.spot.isIndoor() ? STAY_IN : STAY_OUT);
			s.afterSunset = r.sunsetMin > 0 && !s.spot.isIndoor() && s.arrive >= r.sunsetMin;
			km += s.legKm;
			move += s.legMin;
			t = s.leave;
		}
		r.totalKm = km;
		r.moveMin = move;
		r.endMin = t;
	}

	// 분 → "14:10" (자정을 넘기면 다음 날 시각으로)
	static String clock(int min) {
		int m = ((min % 1440) + 1440) % 1440;
		return String.format("%02d:%02d", m / 60, m % 60);
	}
}
