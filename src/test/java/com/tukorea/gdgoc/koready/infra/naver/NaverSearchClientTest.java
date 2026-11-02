package com.tukorea.gdgoc.koready.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 네이버 지역 검색 클라이언트. 응답은 실제 응답(2026-11-02)에서 쓰는 필드만 남겼다.
 */
class NaverSearchClientTest {

	private static final String BASE_URL = "https://naverapihub.apigw.ntruss.com";
	private static final NaverSearchProperties PROPERTIES = new NaverSearchProperties(BASE_URL, "test-id",
			"test-secret", Duration.ofSeconds(1), Duration.ofSeconds(1));

	MockRestServiceServer server;
	NaverSearchClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = NaverSearchClient.configure(RestClient.builder(), PROPERTIES);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new NaverSearchClient(builder.build());
	}

	/**
	 * 키 헤더·Accept를 붙이고 검색어를 인코딩해 보낸다. 네이버 순서 그대로이고, 이름의 강조 태그는 뺀다.
	 * 좌표는 10^7로 나눈 경도·위도, 빈 링크는 null이다
	 */
	@Test
	void searchReadsPlaces() {
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=%EB%AA%85%EB%8F%99%EC%97%AD&display=5"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("X-NCP-APIGW-API-KEY-ID", "test-id"))
				.andExpect(header("X-NCP-APIGW-API-KEY", "test-secret"))
				.andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
				.andRespond(withSuccess("""
						{"lastBuildDate": "Mon, 02 Nov 2026 16:49:57 +0900", "total": 5, "start": 1, "display": 5,
						 "items": [
						   {"title": "<b>명동역<\\/b> 4호선", "link": "", "category": "교통,운수>지하철,전철",
						    "description": "", "telephone": "", "address": "서울특별시 중구 충무로2가 109-2",
						    "roadAddress": "서울특별시 중구 퇴계로 126", "mapx": "1269864649", "mapy": "375609720"},
						   {"title": "명동교자 1호점", "link": "http:\\/\\/www.mdkj.co.kr\\/", "category": "한식>칼국수,만두",
						    "description": "", "telephone": "", "address": "서울특별시 중구 명동2가 33-4 명동교자",
						    "roadAddress": "서울특별시 중구 명동10길 10 명동교자", "mapx": "1269851666", "mapy": "375634828"}]}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.searchLocal("명동역", 5)).containsExactly(
				new LocalSearchPlace("명동역 4호선", null, "서울특별시 중구 퇴계로 126", "서울특별시 중구 충무로2가 109-2",
						37.560972, 126.9864649),
				new LocalSearchPlace("명동교자 1호점", "http://www.mdkj.co.kr/", "서울특별시 중구 명동10길 10 명동교자",
						"서울특별시 중구 명동2가 33-4 명동교자", 37.5634828, 126.9851666));
		server.verify();
	}

	/** 강조 태그를 뺀 뒤 HTML 문자 표기를 글자로 바꾼다 (원래 이름에 든 {@code <b>} 글자는 남는다). 링크는 받은 그대로다 */
	@Test
	void searchUnescapesNames() {
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=H%26M%20%EB%AA%85%EB%8F%99&display=5"))
				.andRespond(withSuccess("""
						{"items": [
						   {"title": "<b>H&amp;M 명동<\\/b> 플래그십 스토어", "link": "https://openine.com/동화고옥?a=1&amp;b=2",
						    "address": "", "roadAddress": "서울특별시 중구 명동8길 3 B1-5층",
						    "mapx": "1269851169", "mapy": "375635458"},
						   {"title": "&lt;b&gt;카페 &quot;숲&quot;", "link": "", "address": "서울특별시 중구 명동2가 1",
						    "roadAddress": "", "mapx": "1269851169", "mapy": "375635458"}]}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.searchLocal("H&M 명동", 5)).containsExactly(
				new LocalSearchPlace("H&M 명동 플래그십 스토어", "https://openine.com/동화고옥?a=1&amp;b=2",
						"서울특별시 중구 명동8길 3 B1-5층", null, 37.5635458, 126.9851169),
				new LocalSearchPlace("<b>카페 \"숲\"", null, null, "서울특별시 중구 명동2가 1", 37.5635458, 126.9851169));
	}

	/** 결과가 없으면 빈 목록, 좌표를 읽지 못한 곳은 빼고 나머지를 준다 */
	@Test
	void searchSkipsPlaceWithoutCoordinates() {
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=x&display=5"))
				.andRespond(withSuccess("""
						{"total": 5, "items": []}
						""", MediaType.APPLICATION_JSON));
		assertThat(client.searchLocal("x", 5)).isEmpty();

		server.reset();
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=x&display=2"))
				.andRespond(withSuccess("""
						{"items": [
						   {"title": "좌표 없음", "roadAddress": "서울특별시 중구 명동길 14", "mapx": "", "mapy": ""},
						   {"title": "명동성당", "roadAddress": "서울특별시 중구 명동길 74", "mapx": "1269872536", "mapy": "375632691"}]}
						""", MediaType.APPLICATION_JSON));
		assertThat(client.searchLocal("x", 2)).containsExactly(
				new LocalSearchPlace("명동성당", null, "서울특별시 중구 명동길 74", null, 37.5632691, 126.9872536));
	}

	/** 키가 틀림(401), 네이버 오류(500), 시간 초과, 읽을 수 없는 응답, items가 없는 응답은 모두 502다 */
	@Test
	void failuresAreBadGateway() {
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=x&display=5"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
						{"error": {"errorCode": "200", "message": "Authentication Failed",
						 "details": "Invalid authentication information."}}
						"""));
		assertBadGateway(() -> client.searchLocal("x", 5));

		server.reset();
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=x&display=5"))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertBadGateway(() -> client.searchLocal("x", 5));

		server.reset();
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=x&display=5"))
				.andRespond(withException(new SocketTimeoutException("timed out")));
		assertBadGateway(() -> client.searchLocal("x", 5));

		server.reset();
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=x&display=5"))
				.andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
		assertBadGateway(() -> client.searchLocal("x", 5));

		server.reset();
		server.expect(requestTo(BASE_URL + "/search/v1/local?query=x&display=5"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertBadGateway(() -> client.searchLocal("x", 5));
	}

	/** 6개 이상을 달라고 하면 네이버가 빈 결과를 주므로 부르기 전에 막는다 */
	@Test
	void countOverMaxIsRejected() {
		assertThatThrownBy(() -> client.searchLocal("x", 6)).isInstanceOf(IllegalArgumentException.class);
		server.verify();
	}

	private static void assertBadGateway(Runnable call) {
		assertThatThrownBy(call::run)
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_FAILED));
	}
}
