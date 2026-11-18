package com.inha.controller;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

// CSS · JS 주소 뒤에 붙일 버전 번호 (예: css/style.css?v=mi4k2x)
// 앱이 다시 올라올 때마다 번호가 바뀌어서, 브라우저가 캐시에 남은 옛 CSS 를 새 화면에 입혀 깨지는 일을 막는다
@WebListener
public class AssetVersionListener implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		sce.getServletContext().setAttribute("assetV", Long.toString(System.currentTimeMillis(), 36));
	}
}
