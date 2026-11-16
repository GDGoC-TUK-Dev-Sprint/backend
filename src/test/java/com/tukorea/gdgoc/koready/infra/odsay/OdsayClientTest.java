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
	private static final String DAILY_QUOTA_EXCEEDED = """
			{"error": [{"code": "429", "message": "Daily quota exceeded"}]}
			""";

	MockRestServiceServer server;
	OdsayClient client;

	@BeforeEach
	void setUp() {
		client = client(KEY, Duration.ZERO, Duration.ofSeconds(1));
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
		OdsayClient noRest = client(KEY, Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
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
		OdsayClient withoutKey = client("", Duration.ZERO, Duration.ofSeconds(1));

		assertThat(withoutKey.enabled()).isFalse();
		assertThat(client.enabled()).isTrue();
		assertBadGateway(() -> withoutKey.searchPath(MYEONGDONG, GYEONGBOKGUNG), "키가 없습니다");
		server.verify();
	}

	/** 호출을 시작하는 사이에 최소 간격을 둔다 */
	@Test
	void callsAreSpacedByMinInterval() {
		OdsayClient spaced = client(KEY, Duration.ofMillis(300), Duration.ofSeconds(1));
		server.expect(times(2), requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		long start = System.nanoTime();
		spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG);
		spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG);

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
		server.verify();
	}

	/** 차례를 기다리는 시간이 응답 시간 제한보다 길면 부르지 않는다 */
	@Test
	void tooLongWaitIsBadGatewayWithoutCalling() {
		OdsayClient spaced = client(KEY, Duration.ofSeconds(5), Duration.ofSeconds(1));
		server.expect(requestTo(URL)).andRespond(withSuccess(ROUTES, MediaType.APPLICATION_JSON));

		spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG);

		assertBadGateway(() -> spaced.searchPath(MYEONGDONG, GYEONGBOKGUNG), "차례");
		server.verify();
	}

	private OdsayClient client(String key, Duration minInterval, Duration readTimeout) {
		return client(key, minInterval, readTimeout, Duration.ofHours(1));
	}

	private OdsayClient client(String key, Duration minInterval, Duration readTimeout, Duration quotaRetryInterval) {
		OdsayProperties properties = new OdsayProperties(BASE_URL, key, Duration.ofSeconds(1), readTimeout, minInterval,
				quotaRetryInterval);
		RestClient.Builder builder = OdsayClient.configure(RestClient.builder(), properties);
		server = MockRestServiceServer.bindTo(builder).build();
		return new OdsayClient(builder.build(), properties);
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
