package com.inha.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// HttpClient 로 API 를 부르고 Gson 으로 JSON 을 읽는 공통 부분
// + 메모리 TTL 캐시 (하루 호출 한도를 아끼려고) + 독립 호출을 동시에 보내는 작은 스레드풀
public class ApiClient {

	// ---- 캐시 유지 시간 (밀리초) ----
	public static final long TTL_LIST = 30 * 60 * 1000L;        // 목록 · 검색 · 주변 · 기상청 예보 30분
	public static final long TTL_DETAIL = 6 * 60 * 60 * 1000L;  // 상세(common · intro · image) 6시간
	public static final long TTL_CODE = 24 * 60 * 60 * 1000L;   // 법정동 코드 24시간
	// 2판 새 API (SPEC2 1절 표)
	public static final long TTL_AIR_NOW = 60 * 60 * 1000L;         // 에어코리아 실시간(시도별) 1시간
	public static final long TTL_AIR_FCST = 3 * 60 * 60 * 1000L;    // 에어코리아 예보 3시간
	public static final long TTL_MID = 12 * 60 * 60 * 1000L;        // 중기예보: 주소에 발표 시각(tmFc)이 들어 있어 길게 둬도 된다 (12시간)
	public static final long TTL_CROWD = 6 * 60 * 60 * 1000L;       // 관광지 집중률 6시간
	public static final long TTL_SUN = 24 * 60 * 60 * 1000L;        // 출몰시각 24시간 (날짜 + 좌표가 주소에 들어감)
	public static final long TTL_UV = 3 * 60 * 60 * 1000L;          // 자외선지수 3시간
	public static final long TTL_WARN = 10 * 60 * 1000L;            // 기상특보 10분

	private static final int CACHE_MAX = 2000;                   // 캐시 항목 상한 (2판은 지역 하나에 칸이 30개쯤 늘어서 넉넉히)
	private static final int SCARCE_MAX = 200;                   // 하루 한도가 빡빡한 API(에어코리아 · 집중률) 전용 캐시 상한
	private static final long DOWN_MILLIS = 90 * 1000L;          // 연결 · 시간 초과 난 API 는 90초 동안 다시 부르지 않는다

	// 하루 호출 한도를 지키려고 실제 호출을 API 별로 센다 (캐시에 있는 건 계속 보임). 서버를 다시 띄우면 0 부터 (메모리 값)
	//  - 관광공사 KorService2 : 개발계정 하루 1,000회 → 700회까지만
	//  - 관광지 집중률 TatsCnctrRateService : 하루 1,000회 → 800회까지만 (KorService2 와 한도가 따로라서 따로 센다)
	//  - 에어코리아 : 하루 500회 → 400회까지만
	private static final String[][] BUDGETS = {
			// { 주소에 들어 있는 글자, 하루 예산, 화면에 보일 이름 }
			{ "/B551011/KorService2/", "700", "관광정보" },
			{ "/B551011/TatsCnctrRateService/", "800", "관광지 붐빔 예측" },
			{ "/B552584/", "400", "대기오염 정보" },
	};
	private static final int[] budgetUsed = new int[BUDGETS.length];
	private static LocalDate budgetDay = LocalDate.now(ZoneId.of("Asia/Seoul"));
	// 재배포(웹앱 다시 올리기)로 메모리 값이 0 으로 돌아가도 하루 예산이 풀리지 않게 임시 폴더 파일에 같이 적어 둔다
	private static final java.nio.file.Path BUDGET_FILE = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "tourweather_budget.txt");
	static {
		loadBudget();
	}

	// HttpClient 안쪽 일꾼 스레드용 풀. 컨텍스트가 끝날 때 같이 닫아야 옛 웹앱이 메모리에 남지 않는다
	// (위의 pool 과 따로 둔다: pool 의 스레드가 모두 send() 로 기다리는 동안 응답 처리 일꾼도 필요해서)
	private static final ExecutorService httpPool = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "tourweather-http");
		t.setDaemon(true);
		return t;
	});
	private static HttpClient client; // 처음 쓸 때 만들고, shutdown 때 버린다

	private static synchronized HttpClient client() {
		if (client == null) {
			// HttpClient 가 만드는 SelectorManager 스레드는 만든 쪽의 클래스로더(웹앱)를 물려받아 재배포 때 옛 웹앱을 붙잡는다
			// → 만드는 동안만 시스템 클래스로더로 바꿔 둔다
			Thread t = Thread.currentThread();
			ClassLoader old = t.getContextClassLoader();
			t.setContextClassLoader(ClassLoader.getSystemClassLoader());
			try {
				client = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(5))
						.executor(httpPool)
						.build();
			} finally {
				t.setContextClassLoader(old);
			}
		}
		return client;
	}

	// 캐시 한 칸: 응답 본문(문자열)과 만료 시각. 트리 대신 문자열을 두면 기상청 큰 응답도 메모리가 덜 든다
	private static class CacheEntry {
		final String body;
		final long expires;
		CacheEntry(String body, long expires) {
			this.body = body;
			this.expires = expires;
		}
	}

	// 가장 오래 들어온 것부터 버리는 맵 (LinkedHashMap 은 넣은 순서를 기억한다)
	private static class BoundedMap extends LinkedHashMap<String, CacheEntry> {
		private static final long serialVersionUID = 1L;
		private final int max;
		BoundedMap(int max) {
			this.max = max;
		}
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
			return size() > max;
		}
	}

	private static final Map<String, CacheEntry> cache = new BoundedMap(CACHE_MAX);
	// 하루 호출 한도가 작은 API(에어코리아 500 · 집중률 1,000)는 따로 담아서, 다른 칸이 많이 쌓여도 밀려나 다시 부르는 일이 없게 한다
	private static final Map<String, CacheEntry> scarceCache = new BoundedMap(SCARCE_MAX);

	private static Map<String, CacheEntry> mapFor(String key) {
		return key.contains("/B552584/") || key.contains("/TatsCnctrRateService/") ? scarceCache : cache;
	}

	// 같은 주소를 동시에 두 번 부르지 않게 하는 자물쇠 (주소마다 하나). 먼저 온 쪽이 부르는 동안 다른 쪽은 기다렸다가 캐시를 쓴다
	private static final Map<String, Object> inflight = new ConcurrentHashMap<>();
	// 연결 · 시간 초과가 난 API(주소의 서비스 부분) → 이 시각까지는 다시 부르지 않는다 (페이지마다 15초씩 멈추는 걸 막는다)
	private static final Map<String, Long> downUntil = new ConcurrentHashMap<>();
	// 방금(5초 안) 연결 · 시간 초과로 실패한 주소 → 그 실패. 같은 주소를 기다리던 스레드가 차례로 또 5~10초씩 기다리지 않게 같은 실패를 바로 돌려준다
	private static final Map<String, Object[]> recentFail = new ConcurrentHashMap<>();

	private static final AtomicInteger liveCalls = new AtomicInteger(); // 실제로 나간 호출 수 (로그 번호용)

	// 독립 호출을 동시에 보내는 작은 스레드풀 (서블릿 컨텍스트가 끝날 때 shutdown)
	private static final ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
		Thread t = new Thread(r, "tourweather-api");
		t.setDaemon(true);
		return t;
	});

	// 「다른 작업을 기다리는 작업」 전용 풀. 위 pool 은 호출만 하는 작업용이라, 그 안에서 다른 호출을 기다리면 풀이 막힐 수 있다.
	// 컨트롤러가 RegionEvidence.load 처럼 안에서 pool 작업을 기다리는 일을 시작할 때 이 풀을 쓴다
	private static final ExecutorService outerPool = Executors.newFixedThreadPool(6, r -> {
		Thread t = new Thread(r, "tourweather-outer");
		t.setDaemon(true);
		return t;
	});

	public static <T> CompletableFuture<T> asyncOuter(Supplier<T> job) {
		return CompletableFuture.supplyAsync(job, outerPool);
	}

	// 실패할 수 있는 호출 한 덩어리 (람다 안에서 ApiException 이라는 검사 예외를 던져야 해서 Supplier 대신 따로 둔다)
	public interface ApiCall<T> {
		T call() throws ApiException;
	}

	// 호출을 풀에서 비동기로 시작한다. 풀 안의 작업은 다른 작업을 또 기다리지 않는다(컨트롤러만 기다림)
	public static <T> CompletableFuture<T> async(ApiCall<T> call) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return call.call();
			} catch (ApiException e) {
				throw new CompletionException(e);
			}
		}, pool);
	}

	// 앞 결과가 나온 뒤에 이어서 하는 호출 (좌표가 나온 뒤 주변 · 날씨 조회처럼)
	public static <A, T> CompletableFuture<T> then(CompletableFuture<A> first, ThrowingFunction<A, T> next) {
		return first.thenApplyAsync(a -> {
			try {
				return next.apply(a);
			} catch (ApiException e) {
				throw new CompletionException(e);
			}
		}, pool);
	}

	// 앞 결과 A 를 받아 T 를 만드는 함수 (역시 ApiException 을 던질 수 있다)
	public interface ThrowingFunction<A, T> {
		T apply(A a) throws ApiException;
	}

	// 비동기 결과를 기다린다. 실패하면 원래 ApiException(사용자에게 보여 줄 문구)을 그대로 던진다
	public static <T> T await(CompletableFuture<T> f) throws ApiException {
		try {
			return f.join();
		} catch (CompletionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof ApiException) throw (ApiException) cause;
			throw new ApiException("처리 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요.");
		}
	}

	// 최대 seconds 초만 기다린다. 넘으면 ApiException (느린 API 하나가 화면 전체를 붙잡지 않게)
	public static <T> T await(CompletableFuture<T> f, int seconds, String apiName) throws ApiException {
		try {
			return f.get(seconds, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			throw new ApiException(apiName + " 응답이 늦어서 이번에는 뺐어요. 잠시 뒤 다시 시도해 주세요.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ApiException(apiName + " 호출이 중단됐어요.");
		} catch (ExecutionException e) {
			if (e.getCause() instanceof ApiException) throw (ApiException) e.getCause();
			throw new ApiException("처리 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요.");
		}
	}

	// 컨텍스트가 끝날 때 불린다
	public static void shutdown() {
		pool.shutdownNow();
		outerPool.shutdownNow();
		httpPool.shutdownNow();
		synchronized (ApiClient.class) {
			client = null; // 참조를 놓아야 HttpClient 의 SelectorManager 스레드도 끝난다 (자바 17 에는 close 가 없다)
		}
		synchronized (cache) {
			cache.clear();
			scarceCache.clear();
		}
		downUntil.clear();
		recentFail.clear();
	}

	// 실제 호출 한 번을 그 API 의 하루 예산에서 뺀다. 예산을 넘으면 ApiException (캐시에 있는 건 이 앞에서 이미 돌려줬다)
	private static synchronized void useBudget(String url) throws ApiException {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		if (!today.equals(budgetDay)) {
			budgetDay = today;
			java.util.Arrays.fill(budgetUsed, 0);
		}
		for (int i = 0; i < BUDGETS.length; i++) {
			if (!url.contains(BUDGETS[i][0])) continue;
			if (budgetUsed[i] >= Integer.parseInt(BUDGETS[i][1])) {
				throw new ApiException("오늘 " + BUDGETS[i][2] + " 조회 횟수가 거의 다 찼어요. 이미 본 곳은 그대로 볼 수 있고, 새로운 조회는 내일 다시 해 주세요.", true);
			}
			budgetUsed[i]++;
			saveBudget();
			return;
		}
	}

	// 예산 파일 읽기: 첫 칸이 오늘 날짜일 때만 쓴다 (한 줄: 날짜;횟수;횟수;횟수). 읽을 수 없으면 0 부터
	private static void loadBudget() {
		try {
			String[] p = new String(java.nio.file.Files.readAllBytes(BUDGET_FILE), StandardCharsets.UTF_8).trim().split(";");
			if (p.length != BUDGETS.length + 1 || !p[0].equals(budgetDay.toString())) return;
			for (int i = 0; i < BUDGETS.length; i++) budgetUsed[i] = Math.max(0, Integer.parseInt(p[i + 1]));
		} catch (Exception e) {
			// 파일이 없거나 깨졌으면 새로 시작
		}
	}

	private static void saveBudget() {
		try {
			StringBuilder sb = new StringBuilder(budgetDay.toString());
			for (int n : budgetUsed) sb.append(';').append(n);
			java.nio.file.Files.write(BUDGET_FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			// 못 적어도 호출은 계속한다
		}
	}

	// 캐시 키 = URL 에서 serviceKey=... 를 뺀 문자열 (키가 캐시 · 로그에 남지 않게)
	static String cacheKey(String url) {
		return url.replaceAll("serviceKey=[^&]*&?", "").replaceAll("[?&]$", "");
	}

	// 캐시 없이 부른다 (예전 호출 방식)
	public static JsonObject getJson(String url, String apiName) throws ApiException {
		return getJson(url, apiName, 0);
	}

	// url 을 호출해서 JSON 최상위 객체를 돌려준다. 실패하면 ApiException
	// ttlMillis > 0 이면 같은 주소(키 제외)를 그 시간 동안 다시 부르지 않고 캐시를 쓴다. 오류 응답은 캐시하지 않는다
	public static JsonObject getJson(String url, String apiName, long ttlMillis) throws ApiException {
		String key = cacheKey(url);
		if (ttlMillis <= 0) return fetch(url, apiName, ttlMillis, key);
		String cached = cacheGet(key);
		if (cached != null) return JsonParser.parseString(cached).getAsJsonObject();
		// 캐시에 없으면: 같은 주소를 동시에 부르는 다른 스레드가 있으면 그 끝나기를 기다렸다가 캐시를 쓴다 (한도가 작은 API 를 아낀다)
		Object lock = inflight.computeIfAbsent(key, k -> new Object());
		try {
			synchronized (lock) {
				cached = cacheGet(key);
				if (cached != null) return JsonParser.parseString(cached).getAsJsonObject();
				Object[] failed = recentFail.get(key);
				if (failed != null && System.currentTimeMillis() < (Long) failed[1]) throw (ApiException) failed[0];
				return fetch(url, apiName, ttlMillis, key);
			}
		} finally {
			inflight.remove(key, lock);
		}
	}

	// 실제로 외부에 나가서 부르는 부분
	private static JsonObject fetch(String url, String apiName, long ttlMillis, String key) throws ApiException {
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
		} catch (IllegalArgumentException e) {
			throw new ApiException(apiName + " 요청 주소가 올바르지 않아요.");
		}

		String service = serviceOf(url);
		Long down = downUntil.get(service);
		if (down != null && System.currentTimeMillis() < down) {
			throw new ApiException(apiName + " 서버가 응답하지 않아서 잠깐 쉬는 중이에요. 1~2분 뒤 다시 시도해 주세요.");
		}
		useBudget(url); // 한도가 빡빡한 API(관광공사 · 집중률 · 에어코리아)는 하루 예산에서 센다

		String body;
		int status;
		try {
			System.out.println("[TourWeather] 외부 호출 #" + liveCalls.incrementAndGet() + " " + key);
			HttpResponse<String> response = client().send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			body = response.body() == null ? "" : response.body().trim();
			status = response.statusCode();
		} catch (HttpConnectTimeoutException e) {
			throw networkFail(key, service, apiName + " 서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
		} catch (HttpTimeoutException e) {
			throw networkFail(key, service, apiName + " 응답이 10초 넘게 없어요. 잠시 뒤 다시 시도해 주세요.");
		} catch (IOException e) {
			throw networkFail(key, service, apiName + " 서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ApiException(apiName + " 호출이 중단됐어요.");
		}

		// 공공데이터포털은 인증 오류를 JSON 을 요청해도 XML 로 돌려줄 때가 있다
		if (body.startsWith("<")) {
			String msg = between(body, "<returnAuthMsg>", "</returnAuthMsg>");
			if (msg.isEmpty()) msg = between(body, "<errMsg>", "</errMsg>");
			if (msg.isEmpty()) msg = between(body, "<resultMsg>", "</resultMsg>");
			throw new ApiException(apiName + " 호출 오류: " + friendly(msg), isLimitMsg(msg));
		}
		JsonObject root;
		try {
			root = JsonParser.parseString(body).getAsJsonObject();
		} catch (Exception e) {
			if (status != 200) {
				throw new ApiException(apiName + " 서버 오류 (HTTP " + status + ")");
			}
			throw new ApiException(apiName + " 응답을 읽지 못했어요.");
		}

		// JSON 으로 온 인증 오류 (미등록 키는 HTTP 403 과 함께 이 형태로 온다)
		if (root.has("OpenAPI_ServiceResponse")) {
			JsonObject h = root.getAsJsonObject("OpenAPI_ServiceResponse").getAsJsonObject("cmmMsgHeader");
			String msg = str(h, "returnAuthMsg");
			if (msg.isEmpty()) msg = str(h, "errMsg");
			throw new ApiException(apiName + " 호출 오류: " + friendly(msg), isLimitMsg(msg));
		}
		if (status != 200) {
			throw new ApiException(apiName + " 서버 오류 (HTTP " + status + ")");
		}
		if (!root.has("response")) {
			throw new ApiException(apiName + " 응답 형식이 예상과 달라요.");
		}

		// 결과 코드가 정상(0000 · 00)일 때만 캐시에 둔다
		if (ttlMillis > 0) {
			String code = resultCode(root);
			if (code.equals("0000") || code.equals("00")) {
				cachePut(key, body, ttlMillis);
			}
		}
		return root;
	}

	// 공공데이터포털이 아닌 곳(길 찾기 RoadRoute)을 같은 HttpClient 로 부른다. 본문과 상태 코드를 그대로 돌려준다(캐시 · 예산은 부르는 쪽이)
	static String[] getPlain(String url, String userAgent, int timeoutSec) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(timeoutSec))
				.header("User-Agent", userAgent)
				.header("Accept", "application/json")
				.GET()
				.build();
		System.out.println("[TourWeather] 외부 호출 #" + liveCalls.incrementAndGet() + " " + url);
		HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return new String[] { String.valueOf(response.statusCode()), response.body() == null ? "" : response.body() };
	}

	// 이 주소의 응답이 캐시에 있나 (있으면 부르지 않아도 된다는 뜻). 공유 링크 남용 막기에서 「새 번호」를 가를 때 쓴다
	public static boolean isCached(String url, long ttlMillis) {
		return ttlMillis > 0 && cacheGet(cacheKey(url)) != null;
	}

	private static String cacheGet(String key) {
		synchronized (cache) {
			Map<String, CacheEntry> m = mapFor(key);
			CacheEntry e = m.get(key);
			if (e == null) return null;
			if (e.expires < System.currentTimeMillis()) {
				m.remove(key);
				return null;
			}
			return e.body;
		}
	}

	private static void cachePut(String key, String body, long ttlMillis) {
		synchronized (cache) {
			mapFor(key).put(key, new CacheEntry(body, System.currentTimeMillis() + ttlMillis));
		}
	}

	// 주소에서 서비스 부분만: ".../1360000/MidFcstInfoService/getMidTa?..." → "/1360000/MidFcstInfoService/"
	static String serviceOf(String url) {
		int s = url.indexOf("//");
		int p = url.indexOf('/', s < 0 ? 0 : s + 2);
		if (p < 0) return url;
		int p2 = url.indexOf('/', p + 1);
		int p3 = p2 < 0 ? -1 : url.indexOf('/', p2 + 1);
		return p3 < 0 ? url.substring(p) : url.substring(p, p3 + 1);
	}

	// 연결 · 시간 초과: 이 서비스를 잠깐 쉬게 하고(새 API 만), 같은 주소를 기다리던 스레드에게 줄 실패를 5초 동안 기억한다
	private static ApiException networkFail(String key, String service, String message) {
		ApiException e = new ApiException(message);
		markDown(service);
		recentFail.put(key, new Object[] { e, System.currentTimeMillis() + 5000L });
		return e;
	}

	// 연결 · 시간 초과가 난 API 를 잠깐 쉬게 한다. 관광공사 목록과 단기예보는 화면의 중심이라 쉬게 하지 않는다 (새 API 만)
	private static void markDown(String service) {
		if (service.contains("/KorService2/") || service.contains("/VilageFcstInfoService")) return;
		downUntil.put(service, System.currentTimeMillis() + DOWN_MILLIS);
	}

	// response.header.resultCode
	public static String resultCode(JsonObject root) {
		JsonObject header = root.getAsJsonObject("response").getAsJsonObject("header");
		return str(header, "resultCode");
	}

	public static String resultMsg(JsonObject root) {
		JsonObject header = root.getAsJsonObject("response").getAsJsonObject("header");
		return str(header, "resultMsg");
	}

	// response.body.items.item 을 배열로 꺼낸다
	// 결과가 0건이면 items 가 "" 로 오고, 1건이면 배열이 아니라 객체로 올 수 있어서 둘 다 처리 (items 자체가 배열인 API 도 있다)
	public static JsonArray items(JsonObject root) {
		JsonArray result = new JsonArray();
		JsonObject response = root.getAsJsonObject("response");
		if (!response.has("body") || !response.get("body").isJsonObject()) return result;
		JsonElement items = response.getAsJsonObject("body").get("items");
		if (items != null && items.isJsonArray()) return items.getAsJsonArray(); // 에어코리아는 items 가 바로 배열
		if (items == null || !items.isJsonObject()) return result;
		JsonElement item = items.getAsJsonObject().get("item");
		if (item == null || item.isJsonNull()) return result;
		if (item.isJsonArray()) return item.getAsJsonArray();
		result.add(item);
		return result;
	}

	// 값이 없거나 null 이면 "" 를 돌려주는 안전한 꺼내기
	public static String str(JsonObject o, String key) {
		if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
		JsonElement e = o.get(key);
		if (e.isJsonPrimitive()) return e.getAsString().trim();
		return "";
	}

	public static double num(JsonObject o, String key) {
		try {
			return Double.parseDouble(str(o, key));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String between(String text, String start, String end) {
		int a = text.indexOf(start);
		if (a < 0) return "";
		int b = text.indexOf(end, a + start.length());
		if (b < 0) return "";
		return text.substring(a + start.length(), b).trim();
	}

	// 하루 호출 한도를 넘었다는 포털 메시지인가
	static boolean isLimitMsg(String msg) {
		return msg != null && (msg.contains("LIMITED_NUMBER") || msg.contains("초과"));
	}

	// 포털 오류 코드를 알아보기 쉬운 말로
	private static String friendly(String msg) {
		if (msg == null || msg.isEmpty()) return "알 수 없는 오류";
		if (msg.contains("SERVICE_KEY_IS_NOT_REGISTERED") || msg.contains("등록되지 않은")) {
			return "등록되지 않은 인증키예요. 공공데이터포털에서 이 API 활용신청이 됐는지 확인해 주세요. (신청 직후면 1~2시간 뒤 풀릴 수 있어요)";
		}
		if (msg.contains("LIMITED_NUMBER") || msg.contains("초과")) {
			return "오늘 호출 횟수 한도를 넘었어요. 내일 다시 시도해 주세요.";
		}
		if (msg.contains("NO_OPENAPI_SERVICE") || msg.contains("폐기")) {
			return "API 서비스가 없거나 폐기됐어요. 주소를 확인해 주세요.";
		}
		return msg;
	}
}
