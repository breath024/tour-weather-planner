package com.inha.model;

// API 호출 실패 · 결과 없음 같은 "사용자에게 보여 줄" 오류
public class ApiException extends Exception {
	private static final long serialVersionUID = 1L;

	private final boolean quotaExceeded; // 하루 호출 한도를 넘었다는 응답인가 (에어코리아처럼 한도가 빡빡한 API 가 하루 쉬는 데 쓴다)

	public ApiException(String message) {
		this(message, false);
	}

	public ApiException(String message, boolean quotaExceeded) {
		super(message);
		this.quotaExceeded = quotaExceeded;
	}

	public boolean isQuotaExceeded() {
		return quotaExceeded;
	}
}
