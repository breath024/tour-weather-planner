package com.inha.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

import com.inha.model.CourseStore;

// 모든 화면 요청 앞에서 공통 값을 request 에 실어 준다 (JSP 마다 헤더 배지 · 담기 폼에서 쓴다)
//   courseCount : 내 코스에 담은 곳 수     courseIds : 담은 contentId 집합 ("담김" 표시용)
//   courseMax   : 최대 곳 수               csrfToken : 담기 · 빼기 폼의 hidden 값
//   flashMsg / flashOk : 방금 처리 결과 알림 (한 번만 보임)
@WebFilter(urlPatterns = { "/plan", "/detail", "/course", "/explore", "/api/*", "/index.jsp" })
public class SessionFilter implements Filter {

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		req.setCharacterEncoding("UTF-8");
		HttpServletRequest request = (HttpServletRequest) req;
		String path = request.getServletPath();
		// JSON 창구(/api/*)는 세션을 새로 만들지 않는다. 화면이 만든 세션이 있을 때만 쓴다 (쿠키 없이 부르면 세션만 쌓이므로)
		HttpSession session = request.getSession(!path.startsWith("/api"));
		if (session == null) {
			chain.doFilter(req, res);
			return;
		}
		req.setAttribute("csrfToken", CourseStore.token(session));
		req.setAttribute("courseCount", CourseStore.count(session));
		req.setAttribute("courseIds", CourseStore.ids(session));
		req.setAttribute("courseMax", CourseStore.MAX);
		boolean isPage = !path.startsWith("/api") && !path.equals("/index.jsp"); // 알림은 화면 요청에서만 꺼낸다
		if ("GET".equals(request.getMethod()) && isPage) {
			String[] flash = CourseStore.takeFlash(session);
			if (flash != null) {
				req.setAttribute("flashMsg", flash[0]);
				req.setAttribute("flashOk", Boolean.parseBoolean(flash[1]));
			}
		}
		chain.doFilter(req, res);
	}
}
