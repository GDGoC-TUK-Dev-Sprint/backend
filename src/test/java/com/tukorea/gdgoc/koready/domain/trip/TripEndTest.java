package com.tukorea.gdgoc.koready.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripRepository;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import com.tukorea.gdgoc.koready.support.TestClockConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 여행 종료(33). "오늘"은 2026-11-01이다 ({@link TestClockConfig}). 종료하면 종료일이 어제(2026-10-31)가 된다.
 * 여행은 SQL로 넣고, 종료 뒤 상태는 여행 저장소와 엔티티로 본다.
 */
@IntegrationTest
class TripEndTest {

	private static final LocalDate TODAY = TestClockConfig.TODAY;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	@Autowired
	TripRepository trips;

	long userId;
	long tripId;

	/** 진행 중인(2일 전~모레) 확정 여행 하나 */
	@BeforeEach
	void createOngoingTrip() {
		userId = insertUser();
		tripId = insertTrip(userId, "CONFIRMED", "2026-10-30", "2026-11-03");
	}

	@Test
	void endingMovesOngoingTripToPast() {
		assertThat(trips.findCurrent(userId, TODAY, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
		assertThat(trips.findPast(userId, TODAY, PageRequest.of(0, 10)).getTotalElements()).isZero();

		assertThat(end(tripId)).hasStatus(HttpStatus.NO_CONTENT).body().isEmpty();
		flush();

		assertThat(endDate()).isEqualTo("2026-10-31");
		assertThat(trips.findCurrent(userId, TODAY, PageRequest.of(0, 10)).getTotalElements()).isZero();
		assertThat(trips.findPast(userId, TODAY, PageRequest.of(0, 10)).getContent()).extracting(Trip::getId)
				.containsExactly(tripId);
		assertThat(trips.findById(tripId).orElseThrow().isCompletedOn(TODAY)).isTrue();
	}

	@Test
	void endedTripIsNoLongerActive() {
		assertThat(trips.findActiveTripIds(userId, TODAY)).containsExactly(tripId);

		assertThat(end(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(trips.findActiveTripIds(userId, TODAY)).isEmpty();
	}

	@Test
	void endingTwiceChangesNothingTheSecondTime() {
		assertThat(end(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(end(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(endDate()).isEqualTo("2026-10-31");
	}

	@Test
	void alreadyFinishedByDateIsNoContentAndKeepsItsEndDate() {
		setTrip("CONFIRMED", "2026-10-01", "2026-10-03");

		assertThat(end(tripId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		assertThat(endDate()).isEqualTo("2026-10-03");
	}

	@ParameterizedTest
	@CsvSource({
			"DRAFT, 2026-10-30, 2026-11-03",
			"CONFIRMED, 2026-11-10, 2026-11-12"})
	void draftOrUpcomingTripIsNotOngoing(String status, String startDate, String endDate) {
		setTrip(status, startDate, endDate);

		assertThat(end(tripId)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
				.isEqualTo("INVALID_REQUEST");
		flush();

		assertThat(endDate()).isEqualTo(endDate);
	}

	/** 시작일 당일에는 종료일을 어제로 당길 수 없다 (종료일 ≥ 시작일 제약) */
	@Test
	void cannotEndOnTheStartDate() {
		setTrip("CONFIRMED", "2026-11-01", "2026-11-03");

		assertThat(end(tripId)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
				.isEqualTo("INVALID_REQUEST");
		flush();

		assertThat(endDate()).isEqualTo("2026-11-03");
	}

	@Test
	void otherUsersTripIsNotFoundAndStays() {
		long other = insertUser();

		assertThat(mvc.post().uri("/api/trips/{tripId}/end", tripId).header("X-Dev-User-Id", other).exchange())
				.hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("TRIP_NOT_FOUND");
		flush();

		assertThat(endDate()).isEqualTo("2026-11-03");
	}

	@Test
	void missingTripIsNotFound() {
		assertThat(end(tripId + 100_000)).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
				.isEqualTo("TRIP_NOT_FOUND");
	}

	@Test
	void needsLogin() {
		assertThat(mvc.post().uri("/api/trips/{tripId}/end", tripId)).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	private MvcTestResult end(long id) {
		return mvc.post().uri("/api/trips/{tripId}/end", id).header("X-Dev-User-Id", userId).exchange();
	}

	private long insertTrip(long ownerId, String status, String startDate, String endDate) {
		return jdbc.queryForObject("""
				INSERT INTO trips (user_id, status, confirmed_at, nationality_code, start_date, end_date, party_size,
				                   stay_type, stay_area_code, stay_name, stay_location, created_at, updated_at)
				VALUES (?, ?, CASE WHEN ? = 'CONFIRMED' THEN now() END, 'JP', ?::date, ?::date, 2,
				        'AREA', 'MYEONGDONG', '명동', ST_GeogFromText('SRID=4326;POINT(126.9849 37.5621)'), now(), now())
				RETURNING id
				""", Long.class, ownerId, status, status, startDate, endDate);
	}

	private void setTrip(String status, String startDate, String endDate) {
		jdbc.update("UPDATE trips SET status = ?, confirmed_at = CASE WHEN ? = 'CONFIRMED' THEN now() END, "
				+ "start_date = ?::date, end_date = ?::date WHERE id = ?", status, status, startDate, endDate, tripId);
		flush();
	}

	private String endDate() {
		return jdbc.queryForObject("SELECT end_date::text FROM trips WHERE id = ?", String.class, tripId);
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}

	/** 쓰기를 DB에 보내고 읽은 엔티티를 비운다 (SQL로 바꾼 값을 다시 읽게) */
	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}
}
