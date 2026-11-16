package com.tukorea.gdgoc.koready.infra.odsay;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * ODsay 대중교통 길찾기 (searchPubTransPathT). 키·시간 제한·호출 간격을 여기서 붙이고, 호출이 실패하거나(연결·시간 초과·HTTP 오류)
 * ODsay가 오류를 주거나(HTTP 200에 {@code error}) 경로를 읽지 못하면 {@code 502 EXTERNAL_API_FAILED}로 바꾼다.
 * 키가 요청 주소(쿼리 파라미터)에 들어가고 HTTP 클라이언트 예외 메시지에는 요청 주소가 통째로 들어가므로, 원인 예외를 붙이지 않고 종류만 남긴다.
 * 일일 호출 한도를 넘기면 쉬는 시간 동안 부르지 않는다 ({@link #enabled}가 false).
 */
@Slf4j
@Component
public class OdsayClient {

	static final String SEARCH_PATH = "/v1/api/searchPubTransPathT";

	/** 호출 수 초과 오류 코드. 초당 호출 수 초과("Too Many Requests")와 일일 한도 초과("Daily quota exceeded")가 같은 코드다 */
	private static final String TOO_MANY_REQUESTS = "429";

	private final RestClient restClient;
	private final String apiKey;
	private final long minIntervalNanos;
	private final long maxWaitNanos;
	private final Duration quotaRetryInterval;

	/** 다음 호출을 시작해도 되는 시각 ({@link System#nanoTime}) */
	private long nextCallAt = System.nanoTime();
	/** 일일 호출 한도를 넘겨 쉬는 중이면 true이고, 쉬는 시간은 {@code quotaPausedUntil}({@link System#nanoTime})까지다 */
	private boolean quotaPaused;
	private long quotaPausedUntil;

	@Autowired
	OdsayClient(RestClient.Builder builder, OdsayProperties properties) {
		this(configure(builder, properties).requestFactory(requestFactory(properties)).build(), properties);
	}

	OdsayClient(RestClient restClient, OdsayProperties properties) {
		this.restClient = restClient;
		this.apiKey = properties.apiKey();
		this.minIntervalNanos = properties.minInterval().toNanos();
		this.maxWaitNanos = properties.readTimeout().toNanos();
		this.quotaRetryInterval = properties.quotaRetryInterval();
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

	/** 키가 있고 일일 호출 한도를 넘겨 쉬는 중이 아니면 true. false면 부르는 쪽이 좌표 추정값을 쓴다 */
	public boolean enabled() {
		return hasKey() && !pausedForQuota();
	}

	/**
	 * 두 지점 사이 대중교통 경로 중 ODsay가 맨 앞에 놓은 것(ODsay 추천순)의 소요시간·거리·운임.
	 * 출발·도착 직선거리가 700m 안이면 ODsay가 거절한다 (오류 -98).
	 */
	public OdsayRoute searchPath(LatLng from, LatLng to) {
		if (!hasKey()) {
			throw failed("키가 없습니다");
		}
		if (pausedForQuota()) {
			throw failed("일일 호출 한도를 넘겨 쉬는 중입니다");
		}
		awaitTurn();
		try {
			return route(restClient.get()
					.uri(SEARCH_PATH + "?apiKey={apiKey}&SX={sx}&SY={sy}&EX={ex}&EY={ey}", apiKey,
							plain(from.lng()), plain(from.lat()), plain(to.lng()), plain(to.lat()))
					.retrieve()
					.body(SearchBody.class));
		} catch (ApiException e) {
			throw e;
		} catch (RestClientResponseException e) {
			throw failed("HTTP " + e.getStatusCode().value());
		} catch (RuntimeException e) {
			throw failed(e.getClass().getSimpleName());
		}
	}

	private boolean hasKey() {
		return apiKey != null && !apiKey.isBlank();
	}

	private synchronized boolean pausedForQuota() {
		return quotaPaused && System.nanoTime() - quotaPausedUntil < 0;
	}

	private void pauseForQuota() {
		synchronized (this) {
			quotaPaused = true;
			quotaPausedUntil = System.nanoTime() + quotaRetryInterval.toNanos();
		}
		log.warn("ODsay 일일 호출 한도를 넘겨 {} 동안 부르지 않습니다", quotaRetryInterval);
	}

	/**
	 * 호출을 시작하는 간격을 둔다 (서버 전체에서 차례를 잡는다). 차례를 기다리는 시간이 응답 시간 제한보다 길면 부르지 않는다
	 * (동선 만들기는 사용자 행을 잠근 채 기다린다).
	 */
	private void awaitTurn() {
		long wait;
		synchronized (this) {
			long now = System.nanoTime();
			wait = Math.max(0, nextCallAt - now);
			if (wait > maxWaitNanos) {
				throw failed("호출 차례를 기다리는 시간이 응답 시간 제한보다 깁니다");
			}
			nextCallAt = now + wait + minIntervalNanos;
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
			if (TOO_MANY_REQUESTS.equals(error.code()) && message != null
					&& message.toLowerCase(Locale.ROOT).contains("quota")) {
				pauseForQuota();
			}
			throw failed("오류 " + error.code() + " " + message);
		}
		List<SearchPath> paths = body.result() == null ? null : body.result().path();
		Info info = paths == null || paths.isEmpty() || paths.getFirst() == null ? null : paths.getFirst().info();
		if (info == null || info.totalTime() == null || info.payment() == null || info.totalDistance() == null) {
			throw failed("경로가 없습니다");
		}
		return new OdsayRoute(info.totalTime(), (int) Math.round(info.totalDistance()), info.payment());
	}

	private static String plain(double value) {
		return BigDecimal.valueOf(value).toPlainString();
	}

	private static ApiException failed(String reason) {
		return new ApiException(ErrorCode.EXTERNAL_API_FAILED, "ODsay 대중교통 길찾기 실패: " + reason);
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
