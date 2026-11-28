package com.tukorea.gdgoc.koready.domain.favorite;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 찜한 장소 목록(13). 장소는 덤프에 있는 것을 쓰고, 사용자·찜은 테스트가 넣는다 (롤백된다).
 */
@IntegrationTest
class FavoriteListTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	long userId;
	long otherId;
	List<Long> placeIds;

	@BeforeEach
	void seed() {
		userId = insertUser();
		otherId = insertUser();
		placeIds = jdbc.queryForList("SELECT id FROM places ORDER BY id LIMIT 4", Long.class);
	}

	@Test
	void emptyWhenNothingIsFavorited() {
		assertThat(list(userId, "")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.items").asArray().isEmpty();
			assertThat(json).extractingPath("$.totalCount").isEqualTo(0);
			assertThat(json).extractingPath("$.hasNext").isEqualTo(false);
		});
	}

	@Test
	void mostRecentlyFavoritedComesFirstWithCardFields() {
		favorite(userId, placeIds.get(0), "2026-10-01 10:00:00+09");
		favorite(userId, placeIds.get(1), "2026-10-03 10:00:00+09");
		favorite(userId, placeIds.get(2), "2026-10-02 10:00:00+09");

		assertThat(list(userId, "")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.totalCount").isEqualTo(3);
			assertThat(json).extractingPath("$.items[0].place.placeId").isEqualTo(placeIds.get(1).intValue());
			assertThat(json).extractingPath("$.items[1].place.placeId").isEqualTo(placeIds.get(2).intValue());
			assertThat(json).extractingPath("$.items[2].place.placeId").isEqualTo(placeIds.get(0).intValue());
			assertThat(json).extractingPath("$.items[0].favoritedAt").asString().startsWith("2026-10-03T10:00:00+09:00");
			assertThat(json).extractingPath("$.items[0].place.name").isNotNull();
			assertThat(json).extractingPath("$.items[0].place.category.code").isNotNull();
			assertThat(json).extractingPath("$.items[0].place.classification.code").isNotNull();
		});
	}

	@Test
	void previewPageKeepsTotalCount() {
		for (int i = 0; i < 4; i++) {
			favorite(userId, placeIds.get(i), "2026-10-0" + (i + 1) + " 10:00:00+09");
		}

		assertThat(list(userId, "?size=3")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.items").asArray().hasSize(3);
			assertThat(json).extractingPath("$.totalCount").isEqualTo(4);
			assertThat(json).extractingPath("$.hasNext").isEqualTo(true);
		});
		assertThat(list(userId, "?size=3&page=1")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.items").asArray().hasSize(1);
			assertThat(json).extractingPath("$.hasNext").isEqualTo(false);
		});
	}

	@Test
	void otherUsersFavoritesAreNotShown() {
		favorite(userId, placeIds.get(0), "2026-10-01 10:00:00+09");
		favorite(otherId, placeIds.get(1), "2026-10-02 10:00:00+09");

		assertThat(list(userId, "")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.totalCount").isEqualTo(1);
			assertThat(json).extractingPath("$.items[0].place.placeId").isEqualTo(placeIds.get(0).intValue());
		});
	}

	@Test
	void removedFavoriteDisappears() {
		favorite(userId, placeIds.get(0), "2026-10-01 10:00:00+09");
		jdbc.update("DELETE FROM favorites WHERE user_id = ? AND place_id = ?", userId, placeIds.get(0));

		assertThat(list(userId, "")).hasStatusOk().bodyJson().extractingPath("$.totalCount").isEqualTo(0);
	}

	@Test
	void invalidPageSizeIsBadRequest() {
		assertThat(list(userId, "?size=51")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void needsLogin() {
		assertThat(mvc.get().uri("/api/favorites")).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	private MvcTestResult list(long asUserId, String query) {
		return mvc.get().uri("/api/favorites" + query).header("X-Dev-User-Id", asUserId).exchange();
	}

	private void favorite(long asUserId, long placeId, String createdAt) {
		jdbc.update("INSERT INTO favorites (user_id, place_id, created_at) VALUES (?, ?, ?::timestamptz)", asUserId, placeId,
				createdAt);
	}

	private long insertUser() {
		return jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id", Long.class);
	}
}
