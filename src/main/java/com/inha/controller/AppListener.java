package com.inha.controller;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import com.inha.model.ApiClient;

// 웹앱이 내려갈 때 API 호출용 스레드풀을 닫는다 (재배포해도 스레드가 쌓이지 않게)
@WebListener
public class AppListener implements ServletContextListener {

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		ApiClient.shutdown();
	}
}
