package com.tukorea.gdgoc.koready.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
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
 * 여행 삭제. "오늘"은 2026-11-01이다 ({@link com.tukorea.gdgoc.koready.support.TestClockConfig}).
 */
@IntegrationTest
class TripDeleteTest {

	private static final String TRIP = """
			{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
			 "categories": ["HS", "SH"], "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
			""";

	private static final List<String> CHILD_TABLES = List.of("trip_categories", "trip_cost_estimates", "trip_expenses");

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	long userId;
	long tripId;

	@BeforeEach
	void createTrip() {
		userId = insertUser();
		assertThat(create(userId)).hasStatus(HttpStatus.CREATED);
		tripId = jdbc.queryForObject("SELECT id FROM trips WHERE user_id = ?", Long.class, userId);
	}

	@Test
	void deletesTripWithEverythingUnderIt() {
		long placeId = jdbc.queryForObject("SELECT id FROM places WHERE content_type_id <> 32 AND active LIMIT 1", Long.class);
		long dayId = jdbc.queryForObject(
				"INSERT INTO trip_days (trip_id, day_number, trip_date) VALUES (?, 1, '2026-11-10') RETURNING id", Long.class,
				tripId);
		long itemId = jdbc.queryForObject("""
				INSERT INTO trip_items (trip_day_id, seq, place_id, origin, arrive_time, depart_time, stay_minutes)
				VALUES (?, 1, ?, 'SELECTED', '09:10', '10:10', 60) RETURNING id
				""", Long.class, dayId, placeId);
		jdbc.update("INSERT INTO legs (trip_day_id, seq, to_item_id, mode, distance_m, duration_minutes, estimated) "
				+ "VALUES (?, 1, ?, 'WALK', 300, 10, true)", dayId, itemId);
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", tripId);
		jdbc.update("INSERT INTO trip_cost_estimates (trip_id, item, min_krw, max_krw, created_at) "
				+ "VALUES (?, 'FOOD', 10000, 20000, now())", tripId);
		jdbc.update("INSERT INTO trip_expenses (trip_id, item, amount_krw, seq) VALUES (?, 'FOOD', 30000, 1)", tripId);
		flush();

		assertThat(delete(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(count("SELECT count(*) FROM trips WHERE id = ?", tripId)).isZero();
		for (String table : CHILD_TABLES) {
			assertThat(count("SELECT count(*) FROM " + table + " WHERE trip_id = ?", tripId)).as(table).isZero();
		}
		assertThat(count("SELECT count(*) FROM trip_days WHERE id = ?", dayId)).isZero();
		assertThat(count("SELECT count(*) FROM trip_items WHERE trip_day_id = ?", dayId)).isZero();
		assertThat(count("SELECT count(*) FROM legs WHERE trip_day_id = ?", dayId)).isZero();
		assertThat(mvc.get().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", userId).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	/** 확정 전·진행 예정·진행 중·끝난 여행 모두 지운다 */
	@ParameterizedTest
	@CsvSource({
			"DRAFT, 2026-11-10, 2026-11-12",
			"CONFIRMED, 2026-11-10, 2026-11-12",
			"CONFIRMED, 2026-10-31, 2026-11-02",
			"CONFIRMED, 2026-10-01, 2026-10-03"})
	void deletesWhateverTheStatus(String status, String startDate, String endDate) {
		jdbc.update("UPDATE trips SET status = ?, start_date = ?::date, end_date = ?::date WHERE id = ?", status, startDate,
				endDate, tripId);
		flush();

		assertThat(delete(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(count("SELECT count(*) FROM trips WHERE id = ?", tripId)).isZero();
	}

	@Test
	void newTripCanBeCreatedAfterDeletingActiveTrip() {
		jdbc.update("UPDATE trips SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", tripId);
		flush();
		assertThat(create(userId)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
				.isEqualTo("ACTIVE_TRIP_EXISTS");

		assertThat(delete(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(create(userId)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void otherUsersTripIsNotFoundAndStays() {
		long other = insertUser();

		MvcTestResult result = mvc.delete().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", other).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("TRIP_NOT_FOUND");
		assertThat(count("SELECT count(*) FROM trips WHERE id = ?", tripId)).isOne();
	}

	@Test
	void deletedTripIsNotFoundTheSecondTime() {
		assertThat(delete(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(delete(tripId)).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
				.isEqualTo("TRIP_NOT_FOUND");
	}

	private MvcTestResult create(long asUserId) {
		MvcTestResult result = mvc.post().uri("/api/trips").header("X-Dev-User-Id", asUserId)
				.contentType(MediaType.APPLICATION_JSON).content(TRIP).exchange();
		flush();
		return result;
	}

	private MvcTestResult delete(long tripId) {
		return mvc.delete().uri("/api/trips/{tripId}", tripId).header("X-Dev-User-Id", userId).exchange();
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	/** 테스트의 요청들은 한 트랜잭션에서 돌므로, 요청이 끝난 것처럼 DB에 쓰고 영속성 컨텍스트를 비운다 */
	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}
}
