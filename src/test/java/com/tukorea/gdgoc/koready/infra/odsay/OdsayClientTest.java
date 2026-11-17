package com.tukorea.gdgoc.koready.infra.odsay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ODsay 대중교통 길찾기 클라이언트. 응답은 실제 응답(2026-11-03, 명동역 → 경복궁)에서 쓰는 필드만 남겼다.
 */
class OdsayClientTest {

	private static final String BASE_URL = "https://api.odsay.com";
	/** 실제 키처럼 '/'가 들어 있다 */
	private static final String KEY = "test/key";
	private static final LatLng MYEONGDONG = new LatLng(37.5609, 126.9863);
	private static final LatLng GYEONGBOKGUNG = new LatLng(37.5796, 126.977);
	private static final String URL = BASE_URL
			+ "/v1/api/searchPubTransPathT?apiKey=test%2Fkey&SX=126.9863&SY=37.5609&EX=126.977&EY=37.5796";
	private static final String ROUTES = """
			{"result": {"searchType": 0, "outTrafficCheck": 0, "pointDistance": 2232,
			  "path": [
			    {"pathType": 1,
			     "info": {"trafficDistance": 4100.0, "totalWalk": 529, "totalTime": 19, "payment": 1550,
			              "busTransitCount": 0, "subwayTransitCount": 2, "firstStartStation": "명동",
			              "lastEndStation": "경복궁", "totalDistance": 4629.0, "totalWalkTime": -1},
			     "subPath": [{"trafficType": 3, "distance": 17, "sectionTime": 1}]},
			    {"pathType": 3,
			     "info": {"trafficDistance": 3172.0, "totalWalk": 501, "totalTime": 21, "payment": 1550,
			              "totalDistance": 3673.0, "totalWalkTime": -1}}]}}
			""";
	private static final String REVERSE_URL = BASE_URL
			+ "/v1/api/searchPubTransPathT?apiKey=test%2Fkey&SX=126.977&SY=37.5796&EX=126.9863&EY=37.5609";
	private static final String TOO_MANY_REQUESTS = """
			{"error": [{"code": "429", "message": "Too Many Requests"}]}
			""";
	private static final String DAILY_QUOTA_EXCEEDED = """
			{"error": [{"code": "429", "message": "Daily quota exceeded"}]}
			""";

	MockRestServiceServer server;
	OdsayClient client;

	@BeforeEach
	void setUp() {
		client = client(settings());
	}

	/** 키를 쿼리 파라미터로 인코딩해 보내고(경도 SX·위도 SY 순서), 경로가 여럿이면 맨 앞 것을 쓴다. 거리는 m로 반올림한다 */
	@Test
	void readsFirstPath() {
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		assertThat(client.searchPath(MYEONGDONG, GYEONGBOKGUNG)).isEqualTo(new OdsayRoute(19, 4629, 1550));
		server.verify();
	}

	/** 출발·도착이 700m 안이거나 정류장이 없는 등의 오류는 HTTP 200에 객체로 온다 */
	@Test
	void errorObjectIsBadGateway() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
				{"error": {"msg": "출, 도착지가 700m이내입니다.", "code": "-98"}}
				""", MediaType.APPLICATION_JSON));

		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "-98");
	}

	/** 키 인증 실패·호출 수 초과는 HTTP 200에 배열로 온다. 초당 호출 수 초과는 쉬지 않고 다음 호출에서 다시 부른다 */
	@Test
	void errorArrayIsBadGateway() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
				{"error": [{"code": "429", "message": "Too Many Requests"}]}
				""", MediaType.APPLICATION_JSON));

		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "429");
		assertThat(client.enabled()).isTrue();
	}

	/** 일일 호출 한도를 넘기면 쉬는 시간 동안 부르지 않는다 */
	@Test
	void dailyQuotaPausesCalls() {
		server.expect(requestTo(URL)).andRespond(withSuccess(DAILY_QUOTA_EXCEEDED, MediaType.APPLICATION_JSON));

		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "Daily quota exceeded");

		assertThat(client.enabled()).isFalse();
		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "일일 호출 한도");
		server.verify();
	}

	@Test
	void callsResumeAfterQuotaRetryInterval() {
		OdsayClient noRest = client(settings().quotaRetry(Duration.ZERO));
		server.expect(requestTo(URL)).andRespond(withSuccess(DAILY_QUOTA_EXCEEDED, MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		assertBadGateway(() -> noRest.searchPath(MYEONGDONG, GYEONGBOKGUNG), "Daily quota exceeded");

		assertThat(noRest.enabled()).isTrue();
		assertThat(noRest.searchPath(MYEONGDONG, GYEONGBOKGUNG)).isEqualTo(new OdsayRoute(19, 4629, 1550));
		server.verify();
	}

	@Test
	void emptyPathIsBadGateway() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
				{"result": {"searchType": 0, "path": []}}
				""", MediaType.APPLICATION_JSON));

		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "경로가 없습니다");
	}

	/** 429가 성공 없이 quota-detect-window 넘게 이어지면 메시지에 "quota"가 없어도 일일 한도로 보고 쉰다 */
	@Test
	void repeatedTooManyRequestsPausesCalls() {
		OdsayClient detecting = client(settings().quotaDetectWindow(Duration.ZERO));
		server.expect(times(2), requestTo(URL)).andRespond(withSuccess(TOO_MANY_REQUESTS, MediaType.APPLICATION_JSON));

		assertBadGateway(() -> detecting.searchPath(MYEONGDONG, GYEONGBOKGUNG), "429");
		assertThat(detecting.enabled()).isTrue();
		assertBadGateway(() -> detecting.searchPath(MYEONGDONG, GYEONGBOKGUNG), "429");

		assertThat(detecting.enabled()).isFalse();
		assertBadGateway(() -> detecting.searchPath(MYEONGDONG, GYEONGBOKGUNG), "일일 호출 한도");
		server.verify();
	}

	/** 사이에 다른 응답이 오면 429가 이어진 것이 아니다 */
	@Test
	void tooManyRequestsStreakEndsOnOtherResponse() {
		OdsayClient detecting = client(settings().quotaDetectWindow(Duration.ZERO));
		server.expect(requestTo(URL)).andRespond(withSuccess(TOO_MANY_REQUESTS, MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(TOO_MANY_REQUESTS, MediaType.APPLICATION_JSON));

		assertBadGateway(() -> detecting.searchPath(MYEONGDONG, GYEONGBOKGUNG), "429");
		detecting.searchPath(MYEONGDONG, GYEONGBOKGUNG);
		assertBadGateway(() -> detecting.searchPath(MYEONGDONG, GYEONGBOKGUNG), "429");

		assertThat(detecting.enabled()).isTrue();
		server.verify();
	}

	/** 429를 받으면 다음 호출을 rate-limit-backoff만큼 늦춘다 */
	@Test
	void tooManyRequestsDelaysNextCall() {
		OdsayClient backingOff = client(settings().rateLimitBackoff(Duration.ofMillis(300)));
		server.expect(requestTo(URL)).andRespond(withSuccess(TOO_MANY_REQUESTS, MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		long start = System.nanoTime();
		assertBadGateway(() -> backingOff.searchPath(MYEONGDONG, GYEONGBOKGUNG), "429");
		backingOff.searchPath(MYEONGDONG, GYEONGBOKGUNG);

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
		server.verify();
	}

	/** HTTP 5xx·연결 실패·시간 초과 뒤에는 쉬는 시간 동안 부르지 않는다 (요청마다 시간 제한만큼 기다리지 않게) */
	@Test
	void serverErrorAndTimeoutPauseCalls() {
		OdsayClient pausing = client(settings().failureRetry(Duration.ofHours(1)));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertBadGateway(() -> pausing.searchPath(MYEONGDONG, GYEONGBOKGUNG), "HTTP 503");
		assertThat(pausing.enabled()).isFalse();
		assertBadGateway(() -> pausing.searchPath(MYEONGDONG, GYEONGBOKGUNG), "쉬는 중");
		server.verify();

		OdsayClient timingOut = client(settings().failureRetry(Duration.ofHours(1)));
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timed out")));

		assertBadGateway(() -> timingOut.searchPath(MYEONGDONG, GYEONGBOKGUNG), "ResourceAccessException");
		assertThat(timingOut.enabled()).isFalse();
		server.verify();
	}

	/** 400대 HTTP 오류는 쉬지 않는다 */
	@Test
	void clientErrorDoesNotPause() {
		OdsayClient pausing = client(settings().failureRetry(Duration.ofHours(1)));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertBadGateway(() -> pausing.searchPath(MYEONGDONG, GYEONGBOKGUNG), "HTTP 400");
		assertThat(pausing.enabled()).isTrue();
	}

	/** 여러 구간은 결과를 요청 순서로 주고, 실패한 구간은 비어 있다 */
	@Test
	void searchPathsKeepsOrderAndEmptiesFailures() {
		OdsayClient batch = client(settings(), true);
		server.expect(requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REVERSE_URL)).andRespond(withSuccess("""
				{"error": {"msg": "검색 결과가 없습니다.", "code": "-99"}}
				""", MediaType.APPLICATION_JSON));

		List<Optional<OdsayRoute>> routes = batch.searchPaths(List.of(
				new OdsayClient.Endpoints(MYEONGDONG, GYEONGBOKGUNG), new OdsayClient.Endpoints(GYEONGBOKGUNG, MYEONGDONG)));

		assertThat(routes).containsExactly(Optional.of(new OdsayRoute(19, 4629, 1550)), Optional.empty());
		server.verify();
	}

	/** 전체 시간 제한(batch-timeout) 안에 차례가 오지 않는 구간은 부르지 않고 비운다 */
	@Test
	void searchPathsStopsAtBatchTimeout() {
		OdsayClient batch = client(settings().minInterval(Duration.ofSeconds(5)).batchTimeout(Duration.ofMillis(500)),
				true);
		server.expect(times(1), requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		long start = System.nanoTime();
		List<Optional<OdsayRoute>> routes = batch.searchPaths(List.of(
				new OdsayClient.Endpoints(MYEONGDONG, GYEONGBOKGUNG), new OdsayClient.Endpoints(MYEONGDONG, GYEONGBOKGUNG)));

		assertThat(routes).filteredOn(Optional::isPresent).hasSize(1);
		assertThat(routes).filteredOn(Optional::isEmpty).hasSize(1);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
		server.verify();
	}

	@Test
	void searchPathsWithoutRequestsDoesNotCall() {
		assertThat(client.searchPaths(List.of())).isEmpty();
		server.verify();
	}

	/** HTTP 오류, 시간 초과, 읽을 수 없는 응답은 모두 502이고, 요청 주소에 든 키는 메시지에도 원인에도 남지 않는다 */
	@Test
	void failuresAreBadGatewayWithoutKey() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "HTTP 500");

		server.reset();
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timed out")));
		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "ResourceAccessException");

		server.reset();
		server.expect(requestTo(URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
		assertBadGateway(() -> client.searchPath(MYEONGDONG, GYEONGBOKGUNG), "실패");
	}

	@Test
	void withoutKeyIsDisabledAndDoesNotCall() {
		OdsayClient withoutKey = client(settings().key(""));

		assertThat(withoutKey.enabled()).isFalse();
		assertThat(client.enabled()).isTrue();
		assertBadGateway(() -> withoutKey.searchPath(MYEONGDONG, GYEONGBOKGUNG), "키가 없습니다");
		server.verify();
	}

	/** 호출을 시작하는 사이에 최소 간격을 둔다 */
	@Test
	void callsAreSpacedByMinInterval() {
		OdsayClient spaced = client(settings().minInterval(Duration.ofMillis(300)));
		server.expect(times(2), requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		long start = System.nanoTime();
		spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG);
		spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG);

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
		server.verify();
	}

	/** 한 구간만 부를 때 차례를 기다리는 시간이 응답 시간 제한보다 길면 부르지 않는다 */
	@Test
	void tooLongWaitIsBadGatewayWithoutCalling() {
		OdsayClient spaced = client(settings().minInterval(Duration.ofSeconds(5)));
		server.expect(requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG);

		assertBadGateway(() -> spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG), "차례");
		server.verify();
	}

	/** 기본은 호출 간격·429 뒤 늦추기·실패 뒤 쉬기 없음, 429가 1시간 이어져야 일일 한도, 일일 한도 뒤 1시간 쉼 */
	private static Settings settings() {
		return new Settings(KEY, Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO, Duration.ofHours(1),
				Duration.ofHours(1), Duration.ZERO);
	}

	private OdsayClient client(Settings settings) {
		return client(settings, false);
	}

	/** 여러 구간을 동시에 부르는 테스트는 기대 순서를 보지 않는다. 응답 시간 제한은 1초다 */
	private OdsayClient client(Settings settings, boolean ignoreOrder) {
		OdsayProperties properties = new OdsayProperties(BASE_URL, settings.key(), Duration.ofSeconds(1),
				Duration.ofSeconds(1), settings.batchTimeout(), settings.minInterval(), settings.rateLimitBackoff(),
				settings.quotaDetectWindow(), settings.quotaRetry(), settings.failureRetry());
		RestClient.Builder builder = OdsayClient.configure(RestClient.builder(), properties);
		server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(ignoreOrder).build();
		return new OdsayClient(builder.build(), properties);
	}

	private record Settings(String key, Duration minInterval, Duration batchTimeout, Duration rateLimitBackoff,
			Duration quotaDetectWindow, Duration quotaRetry, Duration failureRetry) {

		Settings key(String value) {
			return new Settings(value, minInterval, batchTimeout, rateLimitBackoff, quotaDetectWindow, quotaRetry,
					failureRetry);
		}

		Settings minInterval(Duration value) {
			return new Settings(key, value, batchTimeout, rateLimitBackoff, quotaDetectWindow, quotaRetry, failureRetry);
		}

		Settings batchTimeout(Duration value) {
			return new Settings(key, minInterval, value, rateLimitBackoff, quotaDetectWindow, quotaRetry, failureRetry);
		}

		Settings rateLimitBackoff(Duration value) {
			return new Settings(key, minInterval, batchTimeout, value, quotaDetectWindow, quotaRetry, failureRetry);
		}

		Settings quotaDetectWindow(Duration value) {
			return new Settings(key, minInterval, batchTimeout, rateLimitBackoff, value, quotaRetry, failureRetry);
		}

		Settings quotaRetry(Duration value) {
			return new Settings(key, minInterval, batchTimeout, rateLimitBackoff, quotaDetectWindow, value, failureRetry);
		}

		Settings failureRetry(Duration value) {
			return new Settings(key, minInterval, batchTimeout, rateLimitBackoff, quotaDetectWindow, quotaRetry, value);
		}
	}

	private static void assertBadGateway(ThrowingCallable call, String messagePart) {
		assertThatThrownBy(call)
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_FAILED);
					assertThat(e.getCause()).isNull();
				})
				.hasMessageContaining(messagePart)
				.hasMessageNotContaining(KEY)
				.hasMessageNotContaining("test%2Fkey");
	}
}
