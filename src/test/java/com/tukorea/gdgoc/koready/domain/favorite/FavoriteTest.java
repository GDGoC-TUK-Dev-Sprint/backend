package com.tukorea.gdgoc.koready.domain.favorite;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 찜하기·찜 풀기. 장소는 덤프에 있는 것 하나를 쓰고, 사용자는 테스트가 넣는다 (롤백된다).
 */
@IntegrationTest
class FavoriteTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	long userId;
	long placeId;
	long missingPlaceId;

	@BeforeEach
	void seed() {
		userId = jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
		placeId = jdbc.queryForObject("SELECT min(id) FROM places", Long.class);
		missingPlaceId = jdbc.queryForObject("SELECT max(id) + 1000 FROM places", Long.class);
	}

	@Test
	void addingTwiceKeepsOneRow() {
		assertThat(put(userId, placeId)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(put(userId, placeId)).hasStatus(HttpStatus.NO_CONTENT).body().isEmpty();

		assertThat(favoriteCount()).isEqualTo(1);
	}

	@Test
	void removingTwiceLeavesNoRow() {
		assertThat(put(userId, placeId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(delete(userId, placeId)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(delete(userId, placeId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(favoriteCount()).isZero();
	}

	@Test
	void removingMissingPlaceIsStillNoContent() {
		assertThat(delete(userId, missingPlaceId)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void addingMissingPlaceIsPlaceNotFound() {
		assertThat(put(userId, missingPlaceId))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson().extractingPath("$.code").isEqualTo("PLACE_NOT_FOUND");
	}

	@Test
	void needsLogin() {
		assertThat(mvc.put().uri("/api/favorites/{placeId}", placeId))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
		assertThat(mvc.delete().uri("/api/favorites/{placeId}", placeId))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	@Test
	void deletedUserCannotAdd() {
		long deletedUserId = jdbc.queryForObject("SELECT max(id) + 1000 FROM users", Long.class);

		assertThat(put(deletedUserId, placeId))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	private MvcTestResult put(long asUserId, long place) {
		return mvc.put().uri("/api/favorites/{placeId}", place).header("X-Dev-User-Id", asUserId).exchange();
	}

	private MvcTestResult delete(long asUserId, long place) {
		return mvc.delete().uri("/api/favorites/{placeId}", place).header("X-Dev-User-Id", asUserId).exchange();
	}

	private int favoriteCount() {
		return jdbc.queryForObject("SELECT count(*) FROM favorites WHERE user_id = ? AND place_id = ?", Integer.class,
				userId, placeId);
	}
}
