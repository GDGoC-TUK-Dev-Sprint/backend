package com.tukorea.gdgoc.koready.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 조건 다시 저장. "오늘"은 2026-11-01이다 ({@link com.tukorea.gdgoc.koready.support.TestClockConfig}).
 * 테스트마다 명동 지역에 묵는 확정 전 여행을 하나 만들어 두고 시작한다.
 */
@IntegrationTest
class TripUpdateTest {

	private static final String MYEONGDONG = """
			{"type": "AREA", "areaCode": "MYEONGDONG"}""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	long userId;

	long tripId;

	@BeforeEach
	void createDraftTrip() {
		userId = insertUser();
		tripId = create(trip(MYEONGDONG));
	}

	@Test
	void otherConditionsChangeAndRouteStays() throws Exception {
		long dayId = insertRoute();

		MvcTestResult result = update("""
				{"nationality": "US", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 3,
				 "budgetPerPerson": {"amount": 300000, "currency": "KRW"}, "categories": ["FD", "SH"],
				 "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
				""");

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"tripId": %d, "status": "DRAFT", "title": "서울 2박 3일 여행", "nationality": {"code": "US", "name": "미국"},
				 "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 3,
				 "budgetPerPerson": {"amount": 300000, "currency": "KRW"},
				 "categories": [{"code": "SH", "name": "쇼핑"}, {"code": "FD", "name": "음식"}],
				 "stay": {"type": "AREA", "areaCode": "MYEONGDONG", "name": "명동"}, "routeCreated": true}
				""".formatted(tripId));
		assertThat(get()).bodyJson().isStrictlyEqualTo(body(result));
		assertThat(routeRows(dayId)).containsExactly(1L, 1L, 1L);
		assertThat(jdbc.queryForList("SELECT class1_code FROM trip_categories WHERE trip_id = ?", String.class, tripId))
				.containsExactlyInAnyOrder("FD", "SH");
	}

	@Test
	void categoriesAreReplaced() {
		for (List<String> categories : List.of(List.of("HS", "SH"), List.of("SH"), List.of("SH"))) {
			String chosen = categories.stream().map("\"%s\""::formatted).toList().toString();

			assertThat(update(trip(MYEONGDONG).replace("[\"HS\"]", chosen)))
					.bodyJson()
					.extractingPath("$.categories[*].code").asArray()
					.containsExactlyInAnyOrderElementsOf(categories);
			assertThat(jdbc.queryForList("SELECT class1_code FROM trip_categories WHERE trip_id = ?", String.class, tripId))
					.containsExactlyInAnyOrderElementsOf(categories);
		}
	}

	@Test
	void periodChangeDeletesRouteAndTitle() throws Exception {
		long dayId = insertRoute();

		MvcTestResult result = update(trip(MYEONGDONG).replace("2026-11-12", "2026-11-13")
				.replace(", \"categories\": [\"HS\"]", ""));

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"tripId": %d, "title": null, "endDate": "2026-11-13", "nights": 3, "days": 4, "categories": [],
				 "routeCreated": false}
				""".formatted(tripId));
		assertThat(get()).bodyJson().isStrictlyEqualTo(body(result));
		assertThat(routeRows(dayId)).containsExactly(0L, 0L, 0L);
		assertThat(jdbc.queryForMap("SELECT title_ko, title_en FROM trips WHERE id = ?", tripId))
				.containsEntry("title_ko", null)
				.containsEntry("title_en", null);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_categories WHERE trip_id = ?", Long.class, tripId))
				.isZero();
	}

	@Test
	void stayChangeDeletesRoute() {
		String otherArea = """
				{"type": "AREA", "areaCode": "JONGNO"}""";

		for (String stay : List.of(otherArea, lodging(lodgingId()))) {
			long dayId = insertRoute();

			assertThat(update(trip(stay)))
					.bodyJson()
					.isLenientlyEqualTo("""
							{"title": null, "stay": %s, "routeCreated": false}
							""".formatted(stay));
			assertThat(routeRows(dayId)).containsExactly(0L, 0L, 0L);
		}
	}

	@Test
	void sameLodgingKeepsRoute() {
		String lodgingTrip = trip(lodging(lodgingId()));
		tripId = create(lodgingTrip);
		long dayId = insertRoute();

		assertThat(update(lodgingTrip.replace("\"partySize\": 2", "\"partySize\": 4")))
				.bodyJson()
				.isLenientlyEqualTo("""
						{"partySize": 4, "title": "서울 2박 3일 여행", "routeCreated": true}
						""");
		assertThat(routeRows(dayId)).containsExactly(1L, 1L, 1L);
	}

	@Test
	void confirmedTripCannotChange() {
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", tripId);

		assertThat(put(trip(MYEONGDONG).replace("\"partySize\": 2", "\"partySize\": 4")))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "TRIP_CONFIRMED", "fields": []}
						""");
		entityManager.flush();
		assertThat(jdbc.queryForObject("SELECT party_size FROM trips WHERE id = ?", Integer.class, tripId)).isEqualTo(2);
	}

	/** 확정한 여행은 요청 값을 보기 전에 거절한다 (이미 시작한 확정 여행을 같은 날짜로 보내도 409) */
	@Test
	void confirmedTripIsRejectedBeforeConditions() {
		jdbc.update("""
				UPDATE trips SET status = 'CONFIRMED', confirmed_at = now(), start_date = '2026-10-31' WHERE id = ?
				""", tripId);

		assertThat(put(trip(MYEONGDONG).replace("2026-11-10", "2026-10-31").replace("\"partySize\": 2", "\"partySize\": 0")))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("TRIP_CONFIRMED");
	}

	/** 만들기와 달리 끝나지 않은 확정 여행이 있어도 막지 않는다 (만들기와 확정이 겹치면 둘이 같이 있을 수 있다) */
	@Test
	void activeConfirmedTripDoesNotBlock() {
		jdbc.update("""
				INSERT INTO trips (user_id, status, nationality_code, start_date, end_date, party_size, stay_type,
				                   stay_area_code, stay_name, stay_location, confirmed_at, created_at, updated_at)
				SELECT user_id, 'CONFIRMED', nationality_code, '2026-11-01', '2026-11-03', party_size, stay_type,
				       stay_area_code, stay_name, stay_location, now(), now(), now()
				FROM trips WHERE id = ?
				""", tripId);

		assertThat(update(trip(MYEONGDONG).replace("\"partySize\": 2", "\"partySize\": 4")))
				.bodyJson()
				.isLenientlyEqualTo("""
						{"tripId": %d, "status": "DRAFT", "partySize": 4}
						""".formatted(tripId));
	}

	@Test
	void conditionsAreCheckedLikeCreate() {
		assertThat(put("""
				{"nationality": "JP", "startDate": "2026-10-31", "endDate": "2026-11-12", "partySize": 0,
				 "stay": {"type": "PIN"}}
				"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [
						  {"field": "startDate", "reason": "오늘(서울) 이후여야 합니다"},
						  {"field": "endDate", "reason": "여행은 최대 10일입니다"},
						  {"field": "partySize", "reason": "1~10명이어야 합니다"},
						  {"field": "stay.type", "reason": "지도 핀(PIN)은 아직 받지 않습니다"}]}
						""");
		assertThat(put("""
				{"stay": {}}
				"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.fields[*].field").asArray()
				.containsExactlyInAnyOrder("nationality", "startDate", "endDate", "partySize", "stay.type");
		long attractionId = jdbc.queryForObject(
				"SELECT id FROM places WHERE content_type_id <> 32 ORDER BY id LIMIT 1", Long.class);
		assertThat(put(trip(lodging(attractionId))))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("PLACE_NOT_FOUND");
	}

	@Test
	void othersOrMissingTripIsNotFound() {
		long otherUserId = insertUser();
		long missingTripId = jdbc.queryForObject("SELECT max(id) + 1 FROM trips", Long.class);
		String body = trip(MYEONGDONG).replace("\"partySize\": 2", "\"partySize\": 4");

		for (MvcTestResult result : List.of(put(tripId, otherUserId, body), put(missingTripId, userId, body))) {
			assertThat(result)
					.hasStatus(HttpStatus.NOT_FOUND)
					.bodyJson()
					.extractingPath("$.code").isEqualTo("TRIP_NOT_FOUND");
		}
		entityManager.flush();
		assertThat(jdbc.queryForObject("SELECT party_size FROM trips WHERE id = ?", Integer.class, tripId)).isEqualTo(2);
	}

	@Test
	void requiresExistingUserAndNumberId() {
		String body = trip(MYEONGDONG);
		long missingUserId = jdbc.queryForObject("SELECT max(id) + 1 FROM users", Long.class);

		assertThat(mvc.put().uri("/api/trips/{tripId}", tripId).contentType(MediaType.APPLICATION_JSON).content(body))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(put(tripId, missingUserId, body))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("UNAUTHORIZED");
		assertThat(put("abc", userId, body))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "tripId", "reason": "형식이 맞지 않습니다"}]}
						""");
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	private long lodgingId() {
		return jdbc.queryForObject(
				"SELECT id FROM places WHERE content_type_id = 32 AND active ORDER BY id LIMIT 1", Long.class);
	}

	/** 동선을 만든 것처럼 제목과 일자·일정 항목·이동 구간을 하나씩 넣는다. 넣은 일자 ID를 돌려준다 */
	private long insertRoute() {
		jdbc.update("UPDATE trips SET title_ko = '서울 2박 3일 여행', title_en = 'Seoul 3-day trip' WHERE id = ?", tripId);
		long dayId = jdbc.queryForObject(
				"INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, 1, '2026-11-10') RETURNING id",
				Long.class, tripId);
		long itemId = jdbc.queryForObject("""
				INSERT INTO trip_items (trip_day_id, seq, place_id, origin, arrive_time, depart_time, stay_minutes)
				SELECT ?, 1, id, 'SELECTED', '10:00', '11:00', 60 FROM places WHERE content_type_id <> 32 ORDER BY id LIMIT 1
				RETURNING id
				""", Long.class, dayId);
		jdbc.update("""
				INSERT INTO legs (trip_day_id, seq, to_item_id, mode, distance_m, duration_minutes)
				VALUES (?, 0, ?, 'WALK', 500, 8)
				""", dayId, itemId);
		return dayId;
	}

	/** 이 여행의 일자 수, 넣어 둔 일자의 일정 항목 수·이동 구간 수 */
	private List<Long> routeRows(long dayId) {
		return List.of(
				jdbc.queryForObject("SELECT count(*) FROM trip_days WHERE trip_id = ?", Long.class, tripId),
				jdbc.queryForObject("SELECT count(*) FROM trip_items WHERE trip_day_id = ?", Long.class, dayId),
				jdbc.queryForObject("SELECT count(*) FROM legs WHERE trip_day_id = ?", Long.class, dayId));
	}

	/** 여행을 만든다. 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private long create(String body) {
		assertThat(mvc.post().uri("/api/trips")
				.header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		entityManager.clear();
		return jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
	}

	/** 조건을 다시 저장한다. 만들기처럼 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private MvcTestResult update(String body) {
		MvcTestResult result = put(body);
		assertThat(result).hasStatusOk();
		entityManager.flush();
		entityManager.clear();
		return result;
	}

	private MvcTestResult put(String body) {
		return put(tripId, userId, body);
	}

	private MvcTestResult put(Object tripId, long asUserId, String body) {
		return mvc.put().uri("/api/trips/{tripId}", tripId)
				.header("X-Dev-User-Id", asUserId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange();
	}

	private MvcTestResult get() {
		return mvc.get().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", userId).exchange();
	}

	private static String body(MvcTestResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** 2026-11-10~12, 일본 2명, 역사관광. 숙소는 stay(JSON) */
	private static String trip(String stay) {
		return """
				{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2, "categories": ["HS"],
				 "stay": %s}
				""".formatted(stay);
	}

	private static String lodging(long placeId) {
		return """
				{"type": "PLACE", "placeId": %d}""".formatted(placeId);
	}
}
