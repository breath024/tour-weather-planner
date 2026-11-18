package com.inha.model;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import jakarta.servlet.ServletContext;

// 공공데이터포털 인증키 읽기
// 키는 소스코드에 쓰지 않고 WEB-INF/apikey.txt 한 줄에 둔다 (WEB-INF 는 브라우저로 못 열어 봄)
// 관광공사 TourAPI 와 기상청 단기예보는 같은 공공데이터포털 키 하나로 호출한다
public class ApiKey {

	public static String load(ServletContext app) throws ApiException {
		String path = app.getRealPath("/WEB-INF/apikey.txt");
		String key = "";
		try {
			if (path != null) {
				Path p = Paths.get(path);
				if (Files.exists(p)) {
					List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
					for (String line : lines) {
						line = line.trim();
						if (!line.isEmpty() && !line.startsWith("#")) {
							key = line;
							break;
						}
					}
				}
			}
		} catch (IOException e) {
			throw new ApiException("인증키 파일을 읽지 못했어요: " + e.getMessage());
		}
		if (key.isEmpty()) {
			throw new ApiException("인증키가 없어요. WEB-INF/apikey.txt 에 공공데이터포털 인증키를 넣어 주세요.");
		}
		// 포털에는 Encoding 키(%가 들어 있음)와 Decoding 키 두 가지가 있다
		// Encoding 키는 그대로, Decoding 키는 URL 인코딩해서 쓴다
		if (key.contains("%")) {
			return key;
		}
		return URLEncoder.encode(key, StandardCharsets.UTF_8);
	}
}
