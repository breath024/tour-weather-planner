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
		// 처음 보여 줄 지도 중심(한국 가운데)과 확대 단계
		request.setAttribute("startLat", 36.5);
		request.setAttribute("startLon", 127.8);
		request.setAttribute("startZoom", 7);
		request.getRequestDispatcher("/WEB-INF/views/explore.jsp").forward(request, response);
	}
}
