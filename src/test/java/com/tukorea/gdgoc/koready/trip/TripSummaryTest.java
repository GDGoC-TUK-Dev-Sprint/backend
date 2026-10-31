package com.tukorea.gdgoc.koready.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 여행 요약. "오늘"은 2026-11-01이다 ({@link com.tukorea.gdgoc.koready.support.TestClockConfig}).
 */
@IntegrationTest
class TripSummaryTest {

	private static final String AREA_TRIP = """
			{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
			 "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	long userId;

	@BeforeEach
	void createUser() {
		userId = insertUser();
	}

	@Test
	void summaryIsSameAsCreateResponse() throws Exception {
		long placeId = jdbc.queryForObject(
				"SELECT id FROM places WHERE content_type_id = 32 AND active ORDER BY id LIMIT 1", Long.class);
		MvcTestResult created = create("""
				{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
				 "budgetPerPerson": {"amount": 500000, "currency": "KRW"}, "categories": ["HS", "SH"],
				 "stay": {"type": "PLACE", "placeId": %d}}
				""".formatted(placeId));

		assertThat(get(tripId()))
				.hasStatusOk()
				.bodyJson()
				.isStrictlyEqualTo(created.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	@ParameterizedTest
	@CsvSource({
			"2026-11-02, 2026-11-03, UPCOMING",
			"2026-11-01, 2026-11-03, ONGOING",
			"2026-10-30, 2026-11-01, ONGOING",
			"2026-10-29, 2026-10-31, COMPLETED"})
	void progressFollowsSeoulToday(LocalDate startDate, LocalDate endDate, TripProgress progress) {
		create(AREA_TRIP);
		jdbc.update("UPDATE trips SET start_date = ?, end_date = ? WHERE user_id = ?", startDate, endDate, userId);

		assertThat(get(tripId())).hasStatusOk().bodyJson().extractingPath("$.progress").isEqualTo(progress.name());
	}

	@Test
	void confirmedTripWithRouteIsViewOnly() {
		create(AREA_TRIP);
		long tripId = tripId();
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = '2026-10-31T05:00:00Z' WHERE id = ?", tripId);
		jdbc.update("INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, 1, '2026-11-10')", tripId);

		assertThat(get(tripId))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"status": "CONFIRMED", "editable": false, "routeCreated": true,
						 "confirmedAt": "2026-10-31T14:00:00+09:00"}
						""");
	}

	@Test
	void othersOrMissingTripIsNotFound() {
		create(AREA_TRIP);
		long tripId = tripId();
		long otherUserId = insertUser();
		long missingTripId = jdbc.queryForObject("SELECT max(id) + 1 FROM trips", Long.class);

		for (MvcTestResult result : List.of(get(tripId, otherUserId), get(missingTripId, userId))) {
			assertThat(result)
					.hasStatus(HttpStatus.NOT_FOUND)
					.bodyJson()
					.extractingPath("$.code").isEqualTo("TRIP_NOT_FOUND");
		}
	}

	@Test
	void requiresLoginAndNumberId() {
		create(AREA_TRIP);

		assertThat(mvc.get().uri("/api/trips/{tripId}", tripId())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(get("abc", userId))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "tripId", "reason": "형식이 맞지 않습니다"}]}
						""");
	}

	/** 국적·지역 목록이 잠정이라 지난 여행에 목록에서 빠진 코드가 남아 있을 수 있다 */
	@Test
	void storedCodesMissingFromOptionsStillShow() {
		create(AREA_TRIP);
		long tripId = tripId();
		jdbc.update("UPDATE trips SET nationality_code = 'KR', stay_area_code = 'GONE' WHERE id = ?", tripId);

		assertThat(mvc.get().uri("/api/trips/{tripId}", tripId)
				.header("X-Dev-User-Id", userId)
				.header("Accept-Language", "en"))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"nationality": {"code": "KR", "name": "KR"},
						 "stay": {"type": "AREA", "areaCode": "GONE", "name": "명동", "nameKo": "명동"}}
						""");
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	/** 여행을 만든다. 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private MvcTestResult create(String body) {
		MvcTestResult result = mvc.post().uri("/api/trips")
				.header("X-Dev-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		entityManager.clear();
		return result;
	}

	private long tripId() {
		return jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
	}

	private MvcTestResult get(long tripId) {
		return get(tripId, userId);
	}

	private MvcTestResult get(Object tripId, long asUserId) {
		return mvc.get().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", asUserId).exchange();
	}
}
