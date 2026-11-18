package com.inha.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 융합 판단: 관광지(API ①)와 날씨(API ②)를 엮어서 추천을 정한다
public class Recommender {

	// 관광지 이름에 이런 말이 있으면 실내로 본다
	private static final String[] INDOOR_WORDS = { "박물관", "미술관", "기념관", "전시", "과학관", "아쿠아리움",
			"수족관", "체험관", "갤러리", "실내", "도서관", "공연장", "아트센터", "문화원", "천문대", "홍보관", "영화관" };

	// 관광타입(contentTypeId) + 이름으로 실내/실외 가르기
	public static boolean isIndoor(String contentTypeId, String title) {
		if ("14".equals(contentTypeId) || "38".equals(contentTypeId)) {
			return true; // 문화시설 · 쇼핑은 실내
		}
		if (title != null) {
			for (String w : INDOOR_WORDS) {
				if (title.contains(w)) return true;
			}
		}
		return false;
	}

	// 축제 · 행사(15)가 실내인지 야외인지: 분류(lclsSystm3, lclsSystmCode2 표)와 제목으로 가른다.
	//  1 = 실내 (제목에 박물관 · 전시 같은 말 / 연극 · 뮤지컬 · 오페라 · 무용 · 클래식 · 영화 · 넌버벌 / 전시회 · 박람회)
	//  0 = 야외 (축제 EV01)
	// -1 = 모름 (전통공연 · 대중콘서트 · 기타공연 · 스포츠경기 · 기타행사, 또는 분류 없음) → 화면은 야외라고 단정하지 않는다
	public static int eventPlace(String lcls3, String title) {
		if (isIndoor("15", title)) return 1;
		String c = lcls3 == null ? "" : lcls3;
		String[] indoor = { "EV0202", "EV0203", "EV0204", "EV0205", "EV0206", "EV0208", "EV0210", "EV030100", "EV030200" };
		for (String p : indoor) if (c.startsWith(p)) return 1;
		if (c.startsWith("EV01")) return 0;
		return -1;
	}

	// 관광지들 좌표의 가운데(중앙값) → 이 지역 날씨를 볼 지점
	// 평균 대신 중앙값을 써서 섬 같은 멀리 떨어진 곳에 덜 끌려가게 함
	public static double[] center(List<TourVO> spots) {
		List<Double> lats = new ArrayList<>();
		List<Double> lons = new ArrayList<>();
		for (TourVO s : spots) {
			if (s.isHasLocation()) {
				lats.add(s.getLat());
				lons.add(s.getLon());
			}
		}
		if (lats.isEmpty()) return null;
		return new double[] { median(lats), median(lons) };
	}

	private static double median(List<Double> list) {
		Collections.sort(list);
		int n = list.size();
		if (n % 2 == 1) return list.get(n / 2);
		return (list.get(n / 2 - 1) + list.get(n / 2)) / 2.0;
	}

	// 날씨 + 미세먼지 등급 → 추천 방향. 등급이 나쁨(3) 이상이면 실내 쪽으로 바꾸고 마스크 안내를 붙인다 (0 = 자료 없음)
	public static RecommendVO recommend(WeatherVO w, int airGrade) {
		RecommendVO r = recommend(w);
		if (airGrade >= 3) {
			String gradeText = AirVO.gradeText(airGrade);
			if (!RecommendVO.INDOOR.equals(r.getMode())) {
				r.setMode(RecommendVO.INDOOR);
				r.setTitle("미세먼지가 " + gradeText + "이라 실내 관광지를 추천해요");
				r.setReason("하늘은 괜찮지만 미세먼지가 " + gradeText + "이에요");
			} else {
				r.setReason(r.getReason() + " · 미세먼지 " + gradeText);
			}
			r.setMask("KF94 마스크를 챙기고, 야외는 짧게 다니세요");
		}
		return r;
	}

	// 날씨 → 추천 방향
	public static RecommendVO recommend(WeatherVO w) {
		RecommendVO r = new RecommendVO();
		int max = w.getMaxTemp() == null ? 15 : w.getMaxTemp();

		if (w.isRainy()) {
			r.setMode(RecommendVO.INDOOR);
			r.setTitle("실내 관광지 위주로 추천해요");
			String when = w.getRainTime().isEmpty() ? "" : " (" + w.getRainTime() + "부터)";
			r.setReason(w.getRainType() + " 예보" + when);
		} else if (max >= 33) {
			r.setMode(RecommendVO.INDOOR);
			r.setTitle("한낮엔 실내 관광지를 추천해요");
			r.setReason("폭염 수준 더위 (최고 " + max + "°)");
		} else if (max <= 0) {
			r.setMode(RecommendVO.INDOOR);
			r.setTitle("실내 관광지 위주로 추천해요");
			r.setReason("한파 수준 추위 (최고 " + max + "°)");
		} else if (w.getMaxPop() >= 30) {
			r.setMode(RecommendVO.MIXED);
			r.setTitle("실외·실내 모두 괜찮아요 (우산은 챙기기)");
			r.setReason("비가 올 수도 있어요");
		} else {
			r.setMode(RecommendVO.OUTDOOR);
			r.setTitle("실외 관광지 위주로 추천해요");
			r.setReason("비 소식이 없어요");
		}

		r.setClothes(clothes(max));
		if (w.isRainy()) {
			r.setUmbrella("우산을 꼭 챙기세요");
		} else if (w.getMaxPop() >= 30) {
			r.setUmbrella("접는 우산이 있으면 좋아요");
		} else {
			r.setUmbrella("우산 필요 없어요");
		}
		return r;
	}

	// 최고기온 기준 옷차림
	private static String clothes(int max) {
		if (max >= 28) return "반팔 · 반바지";
		if (max >= 23) return "반팔 · 얇은 셔츠";
		if (max >= 20) return "얇은 긴팔 · 면바지";
		if (max >= 17) return "맨투맨 · 얇은 가디건";
		if (max >= 12) return "자켓 · 니트";
		if (max >= 9) return "트렌치코트 · 야상";
		if (max >= 5) return "코트 · 히트텍";
		return "패딩 · 목도리";
	}

	// 이 날씨 추천에 이 관광지가 맞나
	public static boolean fits(TourVO s, RecommendVO r) {
		if (RecommendVO.INDOOR.equals(r.getMode())) return s.isIndoor();
		if (RecommendVO.OUTDOOR.equals(r.getMode())) return !s.isIndoor();
		return true; // MIXED 는 전부 추천
	}

	// 추천 목록 / 나머지 목록 가르기
	public static List<TourVO> pick(List<TourVO> spots, RecommendVO r, boolean recommended) {
		List<TourVO> result = new ArrayList<>();
		for (TourVO s : spots) {
			if (fits(s, r) == recommended) result.add(s);
		}
		return result;
	}

	// 상세 화면: 이 관광지 + 이 곳 날씨로 한 줄 조언
	public static String spotTip(TourVO spot, WeatherVO w) {
		int max = w.getMaxTemp() == null ? 15 : w.getMaxTemp();
		String tip;
		if (w.isRainy()) {
			if (spot.isIndoor()) tip = "비가 와도 실내라 둘러보기 좋아요.";
			else tip = "실외 관광지인데 " + w.getRainType() + " 예보가 있어요. 우비·우산을 챙기거나 다른 날을 골라 보세요.";
		} else if (max >= 33) {
			tip = spot.isIndoor() ? "더운 날 시원하게 둘러보기 좋은 실내예요." : "많이 더워요. 한낮은 피하고 아침·저녁에 가세요.";
		} else if (max <= 0) {
			tip = spot.isIndoor() ? "추운 날 들르기 좋은 실내예요." : "많이 추워요. 따뜻하게 입고 짧게 둘러보세요.";
		} else if (w.getMaxPop() >= 30) {
			tip = spot.isIndoor() ? "날씨와 상관없이 즐길 수 있는 실내예요." : "비 올 수도 있어요. 접는 우산을 챙기세요.";
		} else {
			tip = spot.isIndoor() ? "날씨가 좋으니 근처 실외 명소와 같이 들러 보세요." : "야외 관광하기 좋은 날씨예요.";
		}
		return tip;
	}
}
