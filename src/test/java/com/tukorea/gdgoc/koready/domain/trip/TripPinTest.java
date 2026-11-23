package com.tukorea.gdgoc.koready.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.infra.naver.NaverMapsClient;
import com.tukorea.gdgoc.koready.infra.naver.ReverseGeocodeResult;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 지도 핀(PIN) 숙소로 여행 만들기·조건 다시 저장. 네이버 클라이언트는 가짜이고, 주소 고르기·서울 여부는 핀 위치 주소 API와 같다.
 * "오늘"은 2026-11-01이다 ({@link com.tukorea.gdgoc.koready.support.TestClockConfig}).
 */
@IntegrationTest
class TripPinTest {

	private static final String MYEONGDONG_AREA = """
			{"type": "AREA", "areaCode": "MYEONGDONG"}""";
	private static final String MYEONGDONG_PIN = pin(37.5637, 126.9849);
	private static final ReverseGeocodeResult MYEONGDONG = new ReverseGeocodeResult(
			"1114012700", "서울특별시 중구 명동길 48 청휘빌딩", "서울특별시 중구 명동2가 33-1");
	private static final ReverseGeocodeResult BUNDANG = new ReverseGeocodeResult(
			"4113510900", "경기도 성남시 분당구 경부고속도로 409", "경기도 성남시 분당구 삼평동 421");

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	@MockitoBean
	NaverMapsClient naverMaps;

	long userId;

	@BeforeEach
	void createUser() {
		userId = insertUser();
	}

	/** 핀 주소(도로명, 없으면 지번)가 숙소 이름·주소다. 번역이 없어 영어로 요청해도 국문이다 */
	@Test
	void createsTripAtPin() {
		given(naverMaps.reverseGeocode(37.5637, 126.9849)).willReturn(MYEONGDONG);

		MvcTestResult result = post(trip(MYEONGDONG_PIN), userId, "en");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		entityManager.clear();
		long tripId = jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
		String stay = """
				{"stay": {"type": "PIN", "placeId": null, "areaCode": null, "name": "서울특별시 중구 명동길 48 청휘빌딩",
				          "nameKo": "서울특별시 중구 명동길 48 청휘빌딩", "address": "서울특별시 중구 명동길 48 청휘빌딩",
				          "location": {"lat": 37.5637, "lng": 126.9849}}}
				""";
		assertThat(result).bodyJson().isLenientlyEqualTo(stay);
		assertThat(mvc.get().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", userId)
				.header("Accept-Language", "en"))
				.bodyJson().isLenientlyEqualTo(stay);
		assertThat(jdbc.queryForMap("SELECT * FROM trips WHERE id = ?", tripId))
				.containsEntry("stay_type", "PIN")
				.containsEntry("stay_place_id", null)
				.containsEntry("stay_area_code", null)
				.containsEntry("stay_name", "서울특별시 중구 명동길 48 청휘빌딩")
				.containsEntry("stay_address", "서울특별시 중구 명동길 48 청휘빌딩");
		assertThat(savedStayLocation(tripId)).isEqualTo(new LatLng(37.5637, 126.9849));
	}

	/** 도로명 주소가 없으면 지번 주소가 숙소 이름·주소다 */
	@Test
	void jibunAddressWhenNoRoadAddress() {
		given(naverMaps.reverseGeocode(37.5, 127.0))
				.willReturn(new ReverseGeocodeResult("1165010800", null, "서울특별시 서초구 반포동 1-1"));

		assertThat(post(trip(pin(37.5, 127.0)))).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{"stay": {"type": "PIN", "name": "서울특별시 서초구 반포동 1-1", "address": "서울특별시 서초구 반포동 1-1"}}
				""");
	}

	/** 핀 주소가 숙소 이름 칸(200자)보다 길면 잘라 저장한다. 주소 칸은 300자다 */
	@Test
	void longPinAddressIsCutToColumnLength() {
		String address = "서울특별시 중구 " + "가".repeat(241);
		given(naverMaps.reverseGeocode(37.5637, 126.9849))
				.willReturn(new ReverseGeocodeResult("1114012700", address, null));

		MvcTestResult result = post(trip(MYEONGDONG_PIN));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		assertThat(address).hasSize(250);
		assertThat(jdbc.queryForMap("SELECT stay_name, stay_address FROM trips WHERE user_id = ?", userId))
				.containsEntry("stay_name", address.substring(0, 200))
				.containsEntry("stay_address", address);
		assertThat(result).bodyJson().extractingPath("$.stay.name").isEqualTo(address.substring(0, 200));
	}

	/** 서울 밖이거나 결과가 없는 곳(바다·국외)이면 400이다 */
	@Test
	void pinOutsideSeoulIsRejected() {
		given(naverMaps.reverseGeocode(37.4, 127.1)).willReturn(BUNDANG);
		given(naverMaps.reverseGeocode(35.68, 139.76)).willReturn(new ReverseGeocodeResult(null, null, null));

		for (String pin : List.of(pin(37.4, 127.1), pin(35.68, 139.76))) {
			assertThat(post(trip(pin)))
					.hasStatus(HttpStatus.BAD_REQUEST)
					.bodyJson()
					.isLenientlyEqualTo("""
							{"code": "OUT_OF_SERVICE_AREA", "fields": []}
							""");
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trips WHERE user_id = ?", Long.class, userId)).isZero();
	}

	/** 서울 안이어도 도로명·지번 주소가 모두 없으면 숙소 이름으로 쓸 주소가 없어 400이다 */
	@Test
	void pinWithoutAddressIsRejected() {
		given(naverMaps.reverseGeocode(37.53, 126.936)).willReturn(new ReverseGeocodeResult("1156011000", null, null));

		assertThat(post(trip(pin(37.53, 126.936))))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "stay.location", "reason": "주소가 없는 위치입니다"}]}
						""");
	}

	@Test
	void naverFailure() {
		given(naverMaps.reverseGeocode(anyDouble(), anyDouble())).willThrow(new ApiException(ErrorCode.EXTERNAL_API_FAILED));

		assertThat(post(trip(MYEONGDONG_PIN)))
				.hasStatus(HttpStatus.BAD_GATEWAY)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "EXTERNAL_API_FAILED"}
						""");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trips WHERE user_id = ?", Long.class, userId)).isZero();
	}

	/** 핀 좌표가 없거나 틀리면 400이다. 요청 값 검사에 걸리면 네이버를 부르지 않는다 */
	@Test
	void invalidRequestDoesNotCallNaver() {
		assertThat(post(trip("""
				{"type": "PIN"}""")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "stay.location", "reason": "PIN이면 필수입니다"}]}
						""");
		for (String location : List.of("{\"lat\": 90.1, \"lng\": 126.98}", "{\"lng\": 126.98}")) {
			assertThat(post(trip("""
					{"type": "PIN", "location": %s}""".formatted(location))))
					.hasStatus(HttpStatus.BAD_REQUEST)
					.bodyJson()
					.extractingPath("$.fields[*].field").isEqualTo(List.of("stay.location.lat"));
		}
		assertThat(post(trip(MYEONGDONG_PIN).replace("\"partySize\": 2", "\"partySize\": 0")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.fields[*].field").isEqualTo(List.of("partySize"));
		verifyNoInteractions(naverMaps);
	}

	/** 조건 다시 저장: 핀으로 바꾸거나 핀 좌표가 바뀌면 동선을 지운다. 좌표가 같으면 주소만 달라져도 동선은 그대로다 */
	@Test
	void updateComparesPinByCoordinates() {
		long tripId = create(trip(MYEONGDONG_AREA));
		insertRoute(tripId);
		given(naverMaps.reverseGeocode(37.5637, 126.9849)).willReturn(MYEONGDONG);

		assertThat(update(tripId, trip(MYEONGDONG_PIN))).bodyJson().isLenientlyEqualTo("""
				{"title": null, "stay": {"type": "PIN", "areaCode": null, "name": "서울특별시 중구 명동길 48 청휘빌딩"},
				 "routeCreated": false}
				""");

		insertRoute(tripId);
		given(naverMaps.reverseGeocode(37.5637, 126.9849))
				.willReturn(new ReverseGeocodeResult("1114012700", "서울특별시 중구 명동길 48", "서울특별시 중구 명동2가 33-1"));
		assertThat(update(tripId, trip(MYEONGDONG_PIN).replace("\"partySize\": 2", "\"partySize\": 3")))
				.bodyJson().isLenientlyEqualTo("""
						{"partySize": 3, "title": "서울 2박 3일 여행", "stay": {"name": "서울특별시 중구 명동길 48"},
						 "routeCreated": true}
						""");

		given(naverMaps.reverseGeocode(37.5665, 126.978))
				.willReturn(new ReverseGeocodeResult("1114010300", "서울특별시 중구 세종대로 110", null));
		assertThat(update(tripId, trip(pin(37.5665, 126.978)))).bodyJson().isLenientlyEqualTo("""
				{"title": null, "stay": {"name": "서울특별시 중구 세종대로 110", "location": {"lat": 37.5665, "lng": 126.978}},
				 "routeCreated": false}
				""");
	}

	/** 조건 다시 저장도 서울 밖 핀은 거절하고, 여행은 그대로다 */
	@Test
	void updateRejectsPinOutsideSeoul() {
		long tripId = create(trip(MYEONGDONG_AREA));
		given(naverMaps.reverseGeocode(37.4, 127.1)).willReturn(BUNDANG);

		assertThat(put(tripId, userId, trip(pin(37.4, 127.1))))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("OUT_OF_SERVICE_AREA");
		entityManager.flush();
		assertThat(jdbc.queryForObject("SELECT stay_type FROM trips WHERE id = ?", String.class, tripId)).isEqualTo("AREA");
	}

	/** 확정한 여행·남의 여행은 핀이 아닌 숙소와 같은 순서로 네이버를 부르기 전에 거절한다 */
	@Test
	void confirmedOrOthersTripIsRejectedBeforeNaver() {
		long tripId = create(trip(MYEONGDONG_AREA));
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", tripId);

		assertThat(put(tripId, userId, trip(MYEONGDONG_PIN)))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("TRIP_CONFIRMED");
		assertThat(put(tripId, insertUser(), trip(MYEONGDONG_PIN)))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("TRIP_NOT_FOUND");
		verifyNoInteractions(naverMaps);
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	/** 동선을 만든 것처럼 제목과 일자 하나를 넣는다 */
	private void insertRoute(long tripId) {
		jdbc.update("UPDATE trips SET title_ko = '서울 2박 3일 여행', title_en = 'Seoul 3-day trip' WHERE id = ?", tripId);
		jdbc.update("INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, 1, '2026-11-10')", tripId);
	}

	/** 저장된 숙소 좌표. 응답은 저장 전 메모리의 좌표를 그대로 주므로 DB 값을 따로 본다 */
	private LatLng savedStayLocation(long tripId) {
		return jdbc.queryForObject(
				"SELECT ST_Y(stay_location::geometry), ST_X(stay_location::geometry) FROM trips WHERE id = ?",
				(rs, i) -> new LatLng(rs.getDouble(1), rs.getDouble(2)), tripId);
	}

	/** 여행을 만든다. 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private long create(String body) {
		assertThat(post(body)).hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		entityManager.clear();
		return jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
	}

	/** 조건을 다시 저장한다. 만들기처럼 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private MvcTestResult update(long tripId, String body) {
		MvcTestResult result = put(tripId, userId, body);
		assertThat(result).hasStatusOk();
		entityManager.flush();
		entityManager.clear();
		return result;
	}

	private MvcTestResult post(String body) {
		return post(body, userId, "ko");
	}

	private MvcTestResult post(String body, long asUserId, String language) {
		return mvc.post().uri("/api/trips")
				.header("X-Dev-User-Id", asUserId)
				.header("Accept-Language", language)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange();
	}

	private MvcTestResult put(long tripId, long asUserId, String body) {
		return mvc.put().uri("/api/trips/{tripId}", tripId)
				.header("X-Dev-User-Id", asUserId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange();
	}

	/** 2026-11-10~12, 일본 2명, 역사관광. 숙소는 stay(JSON) */
	private static String trip(String stay) {
		return """
				{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2, "categories": ["HS"],
				 "stay": %s}
				""".formatted(stay);
	}

	private static String pin(double lat, double lng) {
		return """
				{"type": "PIN", "location": {"lat": %s, "lng": %s}}""".formatted(lat, lng);
	}
}
