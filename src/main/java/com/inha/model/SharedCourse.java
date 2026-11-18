package com.inha.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// 공유 링크의 번호들을 관광지 정보로 바꾼 것 (/course?share=… 화면 · /course/ics?share=… 가 같이 쓴다)
// 남이 보낸 번호라 제목 · 좌표 같은 값은 링크에서 읽지 않고, 번호마다 서버가 관광공사(detailCommon2, 6시간 캐시)로 다시 확인한다
public class SharedCourse {
	private final List<CourseItem> items = new ArrayList<>();   // 링크 순서대로, 확인된 곳만
	private int missing;     // 관광공사에 없는 번호
	private int failed;      // 호출이 실패한 번호
	private String firstError = "";

	// ids 는 ShareLink.parse 를 거친 번호(숫자만 · 중복 없음 · 최대 10). 확인은 동시에, 담는 순서는 링크 그대로. 컨트롤러(서블릿 스레드)에서만 부른다
	public static SharedCourse resolve(TourDAO tour, List<String> ids) {
		SharedCourse r = new SharedCourse();
		List<CompletableFuture<TourVO>> futures = new ArrayList<>();
		for (String id : ids) futures.add(ApiClient.async(() -> tour.detail(id)));
		for (CompletableFuture<TourVO> f : futures) {
			try {
				TourVO vo = ApiClient.await(f);
				if (vo == null) r.missing++;
				else r.items.add(CourseItem.from(vo));
			} catch (ApiException e) {
				r.failed++;
				if (r.firstError.isEmpty()) r.firstError = e.getMessage();
			}
		}
		return r;
	}

	// 이 번호들 중 관광공사에 새로 물어봐야 하는 개수: 이 세션이 이미 본 번호 · 내 코스에 든 번호 · 캐시에 있는 번호는 0으로 센다
	public static int newLookups(TourDAO tour, jakarta.servlet.http.HttpSession session, List<String> ids) {
		int n = 0;
		for (String id : ids) {
			if (CourseStore.isShownShareId(session, id) || CourseStore.ids(session).contains(id) || tour.isDetailCached(id)) continue;
			n++;
		}
		return n;
	}

	// 확인하지 못한 곳이 있으면 화면에 알릴 문구 (없으면 "")
	public String notice() {
		if (missing == 0 && failed == 0) return "";
		StringBuilder sb = new StringBuilder();
		if (missing > 0) sb.append("관광공사에 없는 번호 ").append(missing).append("곳");
		if (failed > 0) {
			if (sb.length() > 0) sb.append(" · ");
			sb.append("확인하지 못한 ").append(failed).append("곳");
		}
		sb.append("은 빼고 열었어요.");
		if (failed > 0 && !firstError.isEmpty()) sb.append(" (").append(firstError).append(")");
		return sb.toString();
	}

	public List<CourseItem> getItems() { return items; }
	public int getMissing() { return missing; }
	public int getFailed() { return failed; }

	public List<String> idList() {
		List<String> ids = new ArrayList<>();
		for (CourseItem c : items) ids.add(c.getContentId());
		return ids;
	}
}
