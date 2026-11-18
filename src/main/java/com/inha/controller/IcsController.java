package com.inha.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.inha.model.ApiException;
import com.inha.model.ApiKey;
import com.inha.model.Area;
import com.inha.model.CourseItem;
import com.inha.model.CourseStore;
import com.inha.model.CourseWeather;
import com.inha.model.IcsBuilder;
import com.inha.model.RateLimiter;
import com.inha.model.ShareLink;
import com.inha.model.SharedCourse;
import com.inha.model.TourDAO;

// 내 코스 → 캘린더 파일(.ics) 내려받기
//   GET /course/ics?day=N              내 코스 (day: 0~10, 잘못되면 0)
//   GET /course/ics?share=a,b,c&day=N  공유 링크의 코스 (번호는 서버가 다시 거르고 확인)
//   (/course.ics 도 같은 곳)
// 빈 코스면 파일 대신 안내 문구를 남기고 /course 로 돌려보낸다(PRG). 실제 만들기는 model.IcsBuilder 가 한다
@WebServlet({ "/course/ics", "/course.ics" })
public class IcsController extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		HttpSession session = request.getSession();
		int day = Area.parseDay(request.getParameter("day"));
		String shareRaw = request.getParameter("share");

		List<CourseItem> items;
		String problem = "";
		if (shareRaw != null) {
			ShareLink link = ShareLink.parse(shareRaw);
			items = new ArrayList<>();
			if (link.isEmpty()) {
				problem = "공유 링크에 코스 번호가 없어요.";
			} else {
				try {
					TourDAO tour = new TourDAO(ApiKey.load(getServletContext()));
					// 새로 물어볼 번호 수만 센다 (공유 화면에서 이미 본 번호는 공짜)
					if (RateLimiter.tryAcquireShare(session, SharedCourse.newLookups(tour, session, link.getIds())) > 0) {
						problem = "새로운 공유 코스를 너무 자주 열었어요. 잠시 뒤에 다시 시도해 주세요.";
					} else {
						items = SharedCourse.resolve(tour, link.getIds()).getItems();
						if (items.isEmpty()) problem = "공유된 코스를 불러오지 못했어요.";
					}
				} catch (ApiException e) {
					problem = "공유된 코스를 불러오지 못했어요. " + e.getMessage();
				}
			}
		} else {
			items = CourseStore.list(session);
			if (items.isEmpty()) problem = "내 코스가 비어 있어요. 관광지를 담으면 캘린더 파일(.ics)로 받을 수 있어요.";
		}
		if (items.isEmpty()) {
			// 빈 코스: 파일 대신 안내 문구 + 내 코스 화면
			CourseStore.flash(session, problem, false);
			response.setStatus(HttpServletResponse.SC_SEE_OTHER);
			response.setHeader("Location", request.getContextPath() + "/course?day=" + day);
			return;
		}

		// 날씨 한 줄 (못 구하면 그 곳은 「불러오지 못했어요」). 방금 /course 를 봤다면 캐시라 외부 호출이 없다
		Map<String, String> wx = new HashMap<>();
		try {
			wx = CourseWeather.lines(items, ApiKey.load(getServletContext()), day);
		} catch (ApiException e) {
			// 키 문제 등 → 날씨 없이 만든다
		}

		LocalDate date = Area.dayOf(day);
		String ics = IcsBuilder.build(items, wx, date, Area.dayOptions().get(day)[1], day, baseUrl(request), Instant.now());
		byte[] body = ics.getBytes(StandardCharsets.UTF_8);

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("text/calendar; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"tour-course-" + date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".ics\"");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setContentLength(body.length);
		try (OutputStream out = response.getOutputStream()) {   // 글자 변환 없이 바이트 그대로 (줄바꿈 CRLF 를 지킨다)
			out.write(body);
		}
	}

	// 앱 주소 "http://host:8095/TourWeather/" (일정 메모의 상세 링크용). Host 가 이상하면 "" (링크 줄을 뺀다)
	private static String baseUrl(HttpServletRequest request) {
		String host = request.getServerName();
		if (host == null || !host.matches("[A-Za-z0-9.-]{1,100}")) return "";
		String scheme = request.isSecure() ? "https" : "http";
		int port = request.getServerPort();
		boolean std = (port == 80 && !request.isSecure()) || (port == 443 && request.isSecure()) || port <= 0;
		return scheme + "://" + host + (std ? "" : ":" + port) + request.getContextPath() + "/";
	}
}
