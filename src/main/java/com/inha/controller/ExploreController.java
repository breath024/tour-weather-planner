package com.inha.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import com.inha.model.Area;

// 지도 탐색 화면: 지도에서 아무 곳이나 누르면 /api/around 로 주변 관광지 + 날씨를 받아 보여 준다 (JS 필요)
@WebServlet("/explore")
public class ExploreController extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		int day = Area.parseDay(request.getParameter("day"));
		request.setAttribute("days", Area.dayOptions());
		request.setAttribute("selDay", day);
		// 처음 자리: ?lat=&lon= 이 오면 그 자리(첫 화면 「지도에서 더 보기」), 없으면 화면이 내 위치를 찾고,
		// 위치를 못 받으면 기본 자리(인천 개항장 일대)를 보여 준다
		double[] at = parsePoint(request.getParameter("lat"), request.getParameter("lon"));
		request.setAttribute("startLat", at != null ? at[0] : Area.HOME_LAT);
		request.setAttribute("startLon", at != null ? at[1] : Area.HOME_LON);
		request.setAttribute("startZoom", 14);
		request.setAttribute("startGiven", at != null);
		request.setAttribute("homeName", Area.HOME_NAME);
		request.getRequestDispatcher("/WEB-INF/views/explore.jsp").forward(request, response);
	}

	// 한국 안의 숫자 좌표면 [위도, 경도], 아니면 null
	static double[] parsePoint(String la, String lo) {
		try {
			double lat = Double.parseDouble(la), lon = Double.parseDouble(lo);
			if (!Double.isFinite(lat) || !Double.isFinite(lon)) return null;
			if (lat < 33 || lat > 39 || lon < 124 || lon > 132) return null;
			return new double[] { Math.round(lat * 10000) / 10000.0, Math.round(lon * 10000) / 10000.0 };
		} catch (Exception e) { // null · 숫자 아님
			return null;
		}
	}
}
