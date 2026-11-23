package com.tukorea.gdgoc.koready.infra.odsay;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * ODsay 대중교통 길찾기 (searchPubTransPathT). 키·시간 제한·호출 간격을 여기서 붙이고, 호출이 실패하거나(연결·시간 초과·HTTP 오류)
 * ODsay가 오류를 주거나(HTTP 200에 {@code error}) 경로를 읽지 못하면 {@code 502 EXTERNAL_API_FAILED}로 바꾼다.
 * 키가 요청 주소(쿼리 파라미터)에 들어가고 HTTP 클라이언트 예외 메시지에는 요청 주소가 통째로 들어가므로, 원인 예외를 붙이지 않고 종류만 남긴다.
 * 일일 호출 한도를 넘기거나 ODsay가 응답하지 않으면 쉬는 시간 동안 부르지 않는다 ({@link #enabled}가 false).
 */
@Slf4j
@Component
public class OdsayClient {

	static final String SEARCH_PATH = "/v1/api/searchPubTransPathT";

	/** 호출 수 초과 오류 코드. 초당 호출 수 초과("Too Many Requests")와 일일 한도 초과("Daily quota exceeded")가 같은 코드다 */
	private static final String TOO_MANY_REQUESTS = "429";

	private final RestClient restClient;
	private final String apiKey;
	private final Duration readTimeout;
	private final Duration batchTimeout;
	private final long minIntervalNanos;
	private final long rateLimitBackoffNanos;
	private final long quotaDetectWindowNanos;
	private final Duration quotaRetryInterval;
	private final Duration failureRetryInterval;

	/** 다음 호출을 시작해도 되는 시각 ({@link System#nanoTime}) */
	private long nextCallAt = System.nanoTime();
	/** 쉬는 중이면 true이고, 쉬는 시간은 {@code pausedUntil}({@link System#nanoTime})까지, 이유는 {@code pauseReason}이다 */
	private boolean paused;
	private long pausedUntil;
	private String pauseReason;
	/** 성공 없이 429가 이어지는 중이면 true이고, 첫 429 시각이 {@code firstTooManyAt}({@link System#nanoTime})이다 */
	private boolean tooManyInRow;
	private long firstTooManyAt;

	@Autowired
	OdsayClient(RestClient.Builder builder, OdsayProperties properties) {
		this(configure(builder, properties).requestFactory(requestFactory(properties)).build(), properties);
	}

	OdsayClient(RestClient restClient, OdsayProperties properties) {
		this.restClient = restClient;
		this.apiKey = properties.apiKey();
		this.readTimeout = properties.readTimeout();
		this.batchTimeout = properties.batchTimeout();
		this.minIntervalNanos = properties.minInterval().toNanos();
		this.rateLimitBackoffNanos = properties.rateLimitBackoff().toNanos();
		this.quotaDetectWindowNanos = properties.quotaDetectWindow().toNanos();
		this.quotaRetryInterval = properties.quotaRetryInterval();
		this.failureRetryInterval = properties.failureRetryInterval();
	}

	/** 주소를 붙인다. 시간 제한은 생성자에서 붙인다 (테스트는 가짜 서버를 이 빌더에 묶는다) */
	static RestClient.Builder configure(RestClient.Builder builder, OdsayProperties properties) {
		return builder.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
	}

	private static JdkClientHttpRequestFactory requestFactory(OdsayProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.readTimeout());
		return factory;
	}

	/** 키가 있고 쉬는 중(일일 한도 초과·응답 없음)이 아니면 true. false면 부르는 쪽이 좌표 추정값을 쓴다 */
	public boolean enabled() {
		return hasKey() && !paused();
	}

	/**
	 * 여러 구간을 동시에 부르고(호출 시작 간격은 지킨다) 전체 시간 제한({@code batch-timeout}) 안에 받은 것만 돌려준다.
	 * 결과는 요청 순서이고, 실패했거나 시간 안에 받지 못한 구간은 비어 있다 (이유는 경고 로그). 동선 만들기는 사용자 행을 잠근 채
	 * 부르므로 구간이 많거나 ODsay가 느려도 이 시간을 넘기지 않는다.
	 */
	public List<Optional<OdsayRoute>> searchPaths(List<Endpoints> requests) {
		if (requests.isEmpty()) {
			return List.of();
		}
		long deadline = System.nanoTime() + batchTimeout.toNanos();
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			List<Future<OdsayRoute>> futures = requests.stream()
					.map(request -> executor.submit(() -> search(request.from(), request.to(), deadline)))
					.toList();
			List<Optional<OdsayRoute>> results = new ArrayList<>(futures.size());
			for (Future<OdsayRoute> future : futures) {
				results.add(await(future, deadline));
			}
			return results;
		} finally {
			// 시간 안에 끝나지 않은 호출은 끊는다 (끝나기를 기다리지 않는다)
			executor.shutdownNow();
		}
	}

	/**
	 * 두 지점 사이 대중교통 경로 중 ODsay가 맨 앞에 놓은 것(ODsay 추천순)의 소요시간·거리·운임.
	 * 출발·도착 직선거리가 700m 안이면 ODsay가 거절한다 (오류 -98).
	 */
	public OdsayRoute searchPath(LatLng from, LatLng to) {
		return search(from, to, System.nanoTime() + readTimeout.toNanos());
	}

	/** 차례를 기다린 호출 시작이 {@code latestStart}({@link System#nanoTime})보다 늦으면 부르지 않는다 */
	private OdsayRoute search(LatLng from, LatLng to, long latestStart) {
		if (!hasKey()) {
			throw failed("키가 없습니다");
		}
		requireNotPaused();
		awaitTurn(latestStart);
		// 차례를 기다리는 동안 쉬기 시작했을 수 있다
		requireNotPaused();
		SearchBody body;
		try {
			body = restClient.get()
					.uri(SEARCH_PATH + "?apiKey={apiKey}&SX={sx}&SY={sy}&EX={ex}&EY={ey}", apiKey,
							plain(from.lng()), plain(from.lat()), plain(to.lng()), plain(to.lat()))
					.retrieve()
					.body(SearchBody.class);
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().is5xxServerError()) {
				pause(failureRetryInterval, "ODsay 서버 오류");
			}
			throw failed("HTTP " + e.getStatusCode().value());
		} catch (ResourceAccessException e) {
			// 연결 실패·시간 초과 (전체 시간 제한으로 끊은 호출 포함. 어느 쪽이든 ODsay가 느리다)
			pause(failureRetryInterval, "ODsay 응답 없음");
			throw failed(e.getClass().getSimpleName());
		} catch (RuntimeException e) {
			throw failed(e.getClass().getSimpleName());
		}
		return route(body);
	}

	private Optional<OdsayRoute> await(Future<OdsayRoute> future, long deadline) {
		try {
			return Optional.of(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
		} catch (TimeoutException e) {
			future.cancel(true);
			log.warn("ODsay 대중교통 길찾기 실패: 전체 시간 제한({}) 안에 받지 못했습니다", batchTimeout);
		} catch (ExecutionException e) {
			log.warn("{}", e.getCause() instanceof ApiException api ? api.getMessage() : "ODsay 대중교통 길찾기 실패");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.cancel(true);
		}
		return Optional.empty();
	}

	private boolean hasKey() {
		return apiKey != null && !apiKey.isBlank();
	}

	private synchronized boolean paused() {
		return paused && System.nanoTime() - pausedUntil < 0;
	}

	private void requireNotPaused() {
		String reason;
		synchronized (this) {
			if (!paused()) {
				return;
			}
			reason = pauseReason;
		}
		throw failed(reason + "로 쉬는 중입니다");
	}

	/** 이미 쉬는 중이면 더 늦게 끝나는 쪽을 따른다 */
	private void pause(Duration duration, String reason) {
		synchronized (this) {
			long until = System.nanoTime() + duration.toNanos();
			if (paused() && until - pausedUntil <= 0) {
				return;
			}
			paused = true;
			pausedUntil = until;
			pauseReason = reason;
		}
		log.warn("{}로 {} 동안 ODsay를 부르지 않습니다", reason, duration);
	}

	/**
	 * 호출을 시작하는 간격을 둔다 (서버 전체에서 차례를 잡는다). 차례가 {@code latestStart}보다 늦으면 부르지 않는다
	 * (동선 만들기는 사용자 행을 잠근 채 기다린다).
	 */
	private void awaitTurn(long latestStart) {
		long wait;
		synchronized (this) {
			long now = System.nanoTime();
			long start = now - nextCallAt < 0 ? nextCallAt : now;
			if (start - latestStart > 0) {
				throw failed("호출 차례를 기다리는 시간이 시간 제한보다 깁니다");
			}
			wait = start - now;
			nextCallAt = start + minIntervalNanos;
		}
		if (wait > 0) {
			try {
				Thread.sleep(Duration.ofNanos(wait));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw failed("호출 차례를 기다리다 중단됐습니다");
			}
		}
	}

	/** 오류 응답이거나 경로를 읽지 못하면 502. 경로가 여럿이면 맨 앞 것이다 */
	private OdsayRoute route(SearchBody body) {
		if (body == null) {
			throw failed("빈 응답");
		}
		if (body.error() != null && !body.error().isEmpty() && body.error().getFirst() != null) {
			OdsayError error = body.error().getFirst();
			String message = error.msg() != null ? error.msg() : error.message();
			if (TOO_MANY_REQUESTS.equals(error.code())) {
				onTooManyRequests(message);
			} else {
				endTooManyRequests();
			}
			throw failed("오류 " + error.code() + " " + message);
		}
		endTooManyRequests();
		List<SearchPath> paths = body.result() == null ? null : body.result().path();
		Info info = paths == null || paths.isEmpty() || paths.getFirst() == null ? null : paths.getFirst().info();
		if (info == null || info.totalTime() == null || info.payment() == null || info.totalDistance() == null) {
			throw failed("경로가 없습니다");
		}
		return new OdsayRoute(info.totalTime(), (int) Math.round(info.totalDistance()), info.payment());
	}

	/**
	 * 초당 호출 수 초과와 일일 한도 초과가 같은 429라 메시지 문구에 기대지 않고 이어지는 시간으로 가른다. 429를 받으면 다음 호출을
	 * {@code rate-limit-backoff}만큼 늦추고(초당 호출 수 초과는 이것으로 풀린다), 다른 응답 없이 429가 {@code quota-detect-window}
	 * 넘게 이어지면 일일 한도를 넘긴 것으로 보고 쉰다. 메시지에 "quota"가 있으면 바로 쉰다.
	 */
	private void onTooManyRequests(String message) {
		boolean quota = message != null && message.toLowerCase(Locale.ROOT).contains("quota");
		synchronized (this) {
			long now = System.nanoTime();
			if (!tooManyInRow) {
				tooManyInRow = true;
				firstTooManyAt = now;
			}
			quota |= now - firstTooManyAt > quotaDetectWindowNanos;
			long backoffUntil = now + rateLimitBackoffNanos;
			if (backoffUntil - nextCallAt > 0) {
				nextCallAt = backoffUntil;
			}
		}
		if (quota) {
			pause(quotaRetryInterval, "일일 호출 한도 초과");
		}
	}

	/** 429가 아닌 응답을 받았다 */
	private synchronized void endTooManyRequests() {
		tooManyInRow = false;
	}

	private static String plain(double value) {
		return BigDecimal.valueOf(value).toPlainString();
	}

	private static ApiException failed(String reason) {
		return new ApiException(ErrorCode.EXTERNAL_API_FAILED, "ODsay 대중교통 길찾기 실패: " + reason);
	}

	/** 구간 하나의 출발·도착 */
	public record Endpoints(LatLng from, LatLng to) {
	}

	/**
	 * 길찾기 응답 (필요한 필드만). 오류는 HTTP 200에 {@code error}로 온다: 입력·경로 오류는 객체({@code code}·{@code msg}),
	 * 키 인증 실패·호출 수 초과(429)는 배열({@code code}·{@code message})이다
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearchBody(SearchResult result,
			@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<OdsayError> error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OdsayError(String code, String msg, String message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearchResult(List<SearchPath> path) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearchPath(Info info) {
	}

	/** totalTime 분, payment 원(교통카드 성인), totalDistance m(걷는 거리 포함, 소수로 온다) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Info(Integer totalTime, Integer payment, Double totalDistance) {
	}
}
