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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 네이버 Maps 클라이언트. 응답은 실제 응답(2026-11-02)에서 쓰는 필드만 남겼다.
 */
class NaverMapsClientTest {

	private static final String BASE_URL = "https://maps.apigw.ntruss.com";
	private static final NaverMapsProperties PROPERTIES = new NaverMapsProperties(BASE_URL, "test-id", "test-secret",
			Duration.ofSeconds(1), Duration.ofSeconds(1));

	MockRestServiceServer server;
	NaverMapsClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = NaverMapsClient.configure(RestClient.builder(), PROPERTIES);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new NaverMapsClient(builder.build());
	}

	/** 키 헤더를 붙이고 검색어를 인코딩해 보낸다. 빈 주소는 null, 시도 이름은 주소 요소에서 꺼낸다 */
	@Test
	void geocodeReadsAddresses() {
		server.expect(requestTo(BASE_URL + "/map-geocode/v2/geocode?query=%EB%AA%85%EB%8F%99%EA%B8%B8%2014&count=20"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("x-ncp-apigw-api-key-id", "test-id"))
				.andExpect(header("x-ncp-apigw-api-key", "test-secret"))
				.andRespond(withSuccess("""
						{"status": "OK", "meta": {"totalCount": 2, "page": 1, "count": 2},
						 "addresses": [
						   {"roadAddress": "서울특별시 중구 명동길 14 Noon Square",
						    "jibunAddress": "서울특별시 중구 명동2가 83-5 Noon Square",
						    "englishAddress": "14, Myeongdong-gil, Jung-gu, Seoul, Republic of Korea",
						    "addressElements": [
						      {"types": ["SIDO"], "longName": "서울특별시", "shortName": "서울특별시", "code": ""},
						      {"types": ["SIGUGUN"], "longName": "중구", "shortName": "중구", "code": ""}],
						    "x": "126.9828804", "y": "37.5634327", "distance": 0.0},
						   {"roadAddress": "", "jibunAddress": "강원특별자치도 춘천시 중앙로2가 23-1", "englishAddress": "",
						    "addressElements": [{"types": ["SIDO"], "longName": "강원특별자치도", "shortName": "", "code": ""}],
						    "x": "127.7270855", "y": "37.8793856", "distance": 0.0}],
						 "errorMessage": ""}
						""", MediaType.APPLICATION_JSON));

		List<GeocodeAddress> addresses = client.geocode("명동길 14", 20);

		assertThat(addresses).containsExactly(
				new GeocodeAddress("서울특별시 중구 명동길 14 Noon Square", "서울특별시 중구 명동2가 83-5 Noon Square",
						"14, Myeongdong-gil, Jung-gu, Seoul, Republic of Korea", "서울특별시", 37.5634327, 126.9828804),
				new GeocodeAddress(null, "강원특별자치도 춘천시 중앙로2가 23-1", null, "강원특별자치도", 37.8793856, 127.7270855));
		server.verify();
	}

	/** 검색어의 예약 문자(+, &)는 값으로 인코딩한다 */
	@Test
	void geocodeEncodesReservedCharacters() {
		server.expect(requestTo(BASE_URL + "/map-geocode/v2/geocode?query=a%2Bb%26c&count=5"))
				.andRespond(withSuccess("""
						{"status": "OK", "meta": {"totalCount": 0, "count": 0}, "addresses": [], "errorMessage": ""}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.geocode("a+b&c", 5)).isEmpty();
		server.verify();
	}

	@Test
	void geocodeErrorStatusIsBadGateway() {
		server.expect(requestTo(BASE_URL + "/map-geocode/v2/geocode?query=x&count=5"))
				.andRespond(withSuccess("""
						{"status": "INVALID_REQUEST", "errorMessage": "query is INVALID"}
						""", MediaType.APPLICATION_JSON));

		assertBadGateway(() -> client.geocode("x", 5));
	}

	/** 키가 틀림(401), 네이버 오류(500), 시간 초과, 읽을 수 없는 응답은 모두 502다 */
	@Test
	void failuresAreBadGateway() {
		server.expect(requestTo(BASE_URL + "/map-geocode/v2/geocode?query=x&count=5"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
						{"error": {"errorCode": "200", "message": "Authentication Failed"}}
						"""));
		assertBadGateway(() -> client.geocode("x", 5));

		server.reset();
		server.expect(requestTo(BASE_URL + "/map-geocode/v2/geocode?query=x&count=5"))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertBadGateway(() -> client.geocode("x", 5));

		server.reset();
		server.expect(requestTo(BASE_URL + "/map-reversegeocode/v2/gc?coords=126.9849,37.5637"
						+ "&orders=legalcode,roadaddr,addr&output=json"))
				.andRespond(withException(new SocketTimeoutException("timed out")));
		assertBadGateway(() -> client.reverseGeocode(37.5637, 126.9849));

		server.reset();
		server.expect(requestTo(BASE_URL + "/map-geocode/v2/geocode?query=x&count=5"))
				.andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
		assertBadGateway(() -> client.geocode("x", 5));
	}

	/** 좌표를 읽지 못한 주소 한 곳은 빼고 나머지를 준다 */
	@Test
	void geocodeSkipsAddressWithoutCoordinates() {
		server.expect(requestTo(BASE_URL + "/map-geocode/v2/geocode?query=x&count=5"))
				.andRespond(withSuccess("""
						{"status": "OK", "addresses": [
						  {"roadAddress": "서울특별시 중구 명동길 14", "x": "", "y": ""},
						  {"roadAddress": "서울특별시 중구 명동길 48", "x": "126.98", "y": "37.56"}]}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.geocode("x", 5)).containsExactly(
				new GeocodeAddress("서울특별시 중구 명동길 48", null, null, null, 37.56, 126.98));
	}

	/** 좌표는 경도,위도 순서로 보낸다. 도로명 주소는 동 없이 도로명·건물번호·건물 이름, 지번 주소는 동과 본번-부번이다 */
	@Test
	void reverseGeocodeFormatsAddresses() {
		server.expect(requestTo(BASE_URL + "/map-reversegeocode/v2/gc?coords=126.9849,37.5637"
						+ "&orders=legalcode,roadaddr,addr&output=json"))
				.andExpect(header("x-ncp-apigw-api-key-id", "test-id"))
				.andExpect(header("x-ncp-apigw-api-key", "test-secret"))
				.andRespond(withSuccess(reverseBody(
						result("legalcode", "1114012700", "서울특별시", "중구", "명동2가", "", null),
						result("roadaddr", "1114012700", "서울특별시", "중구", "명동2가", "", """
								{"type": "", "name": "명동길", "number1": "48", "number2": "",
								 "addition0": {"type": "building", "value": "청휘빌딩"},
								 "addition1": {"type": "zipcode", "value": "04536"}}"""),
						result("addr", "1114012700", "서울특별시", "중구", "명동2가", "", """
								{"type": "1", "number1": "33", "number2": "1", "addition0": {"type": "", "value": ""}}""")),
						MediaType.APPLICATION_JSON));

		assertThat(client.reverseGeocode(37.5637, 126.9849)).isEqualTo(new ReverseGeocodeResult(
				"1114012700", "서울특별시 중구 명동길 48 청휘빌딩", "서울특별시 중구 명동2가 33-1"));
		server.verify();
	}

	/** 읍·면은 도로명 주소에 들어가고, 건물번호에도 부번이 있다. 지번이 산이면 "산"을 붙인다 */
	@Test
	void reverseGeocodeOutsideSeoul() {
		server.expect(requestTo(BASE_URL + "/map-reversegeocode/v2/gc?coords=127.704724,38.1052948"
						+ "&orders=legalcode,roadaddr,addr&output=json"))
				.andRespond(withSuccess(reverseBody(
						result("legalcode", "5179025022", "강원특별자치도", "화천군", "화천읍", "하리", null),
						result("roadaddr", "5179025022", "강원특별자치도", "화천군", "화천읍", "하리", """
								{"type": "", "name": "중앙로", "number1": "1", "number2": "",
								 "addition0": {"type": "building", "value": "새마을금고"}}""")),
						MediaType.APPLICATION_JSON));
		assertThat(client.reverseGeocode(38.1052948, 127.704724)).isEqualTo(new ReverseGeocodeResult(
				"5179025022", "강원특별자치도 화천군 화천읍 중앙로 1 새마을금고", null));

		server.reset();
		server.expect(requestTo(BASE_URL + "/map-reversegeocode/v2/gc?coords=126.9779,37.6589"
						+ "&orders=legalcode,roadaddr,addr&output=json"))
				.andRespond(withSuccess(reverseBody(
						result("legalcode", "4128110700", "경기도", "고양시 덕양구", "북한동", "", null),
						result("roadaddr", "4128110700", "경기도", "고양시 덕양구", "북한동", "", """
								{"type": "", "name": "대서문길", "number1": "197", "number2": "20",
								 "addition0": {"type": "", "value": ""}}"""),
						result("addr", "4128110700", "경기도", "고양시 덕양구", "북한동", "", """
								{"type": "2", "number1": "1", "number2": "1", "addition0": {"type": "", "value": ""}}""")),
						MediaType.APPLICATION_JSON));
		assertThat(client.reverseGeocode(37.6589, 126.9779)).isEqualTo(new ReverseGeocodeResult(
				"4128110700", "경기도 고양시 덕양구 대서문길 197-20", "경기도 고양시 덕양구 북한동 산1-1"));
	}

	/** 바다·국외는 결과 없음(상태 3)이라 모두 null이다. 그 밖의 오류 상태는 502다 */
	@Test
	void reverseGeocodeNoResults() {
		server.expect(requestTo(BASE_URL + "/map-reversegeocode/v2/gc?coords=139.76,35.68"
						+ "&orders=legalcode,roadaddr,addr&output=json"))
				.andRespond(withSuccess("""
						{"status": {"code": 3, "name": "no results", "message": "요청한 데이타의 결과가 없습니다."}}
						""", MediaType.APPLICATION_JSON));
		assertThat(client.reverseGeocode(35.68, 139.76)).isEqualTo(new ReverseGeocodeResult(null, null, null));

		server.reset();
		server.expect(requestTo(BASE_URL + "/map-reversegeocode/v2/gc?coords=139.76,35.68"
						+ "&orders=legalcode,roadaddr,addr&output=json"))
				.andRespond(withSuccess("""
						{"status": {"code": 900, "name": "unexpected error", "message": "error"}}
						""", MediaType.APPLICATION_JSON));
		assertBadGateway(() -> client.reverseGeocode(35.68, 139.76));
	}

	private static void assertBadGateway(Runnable call) {
		assertThatThrownBy(call::run)
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_FAILED));
	}

	private static String reverseBody(String... results) {
		return """
				{"status": {"code": 0, "name": "ok", "message": "done"}, "results": [%s]}
				""".formatted(String.join(",", results));
	}

	private static String result(String name, String legalCode, String area1, String area2, String area3,
			String area4, String land) {
		return """
				{"name": "%s", "code": {"id": "%s", "type": "L", "mappingId": "x"},
				 "region": {"area0": {"name": "kr"}, "area1": {"name": "%s"}, "area2": {"name": "%s"},
				            "area3": {"name": "%s"}, "area4": {"name": "%s"}}%s}
				""".formatted(name, legalCode, area1, area2, area3, area4, land == null ? "" : ", \"land\": " + land);
	}
}
