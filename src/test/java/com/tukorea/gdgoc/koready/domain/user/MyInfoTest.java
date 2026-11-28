package com.tukorea.gdgoc.koready.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 내 정보(6)와 회원 탈퇴(7). 사용자는 테스트가 넣는다 (롤백된다).
 */
@IntegrationTest
class MyInfoTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	@Test
	void idAccountShowsLoginIdAndIdMethod() {
		long userId = insertLocalUser("hong_01", "홍길동");

		assertThat(me(userId)).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.userId").isEqualTo((int) userId);
			assertThat(json).extractingPath("$.name").isEqualTo("홍길동");
			assertThat(json).extractingPath("$.loginId").isEqualTo("hong_01");
			assertThat(json).extractingPath("$.loginMethod").isEqualTo("ID");
		});
	}

	@Test
	void socialAccountShowsProviderAndNoLoginId() {
		long userId = insertSocialUser("GOOGLE", "Sato Yui");

		assertThat(me(userId)).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.name").isEqualTo("Sato Yui");
			assertThat(json).extractingPath("$.loginId").isNull();
			assertThat(json).extractingPath("$.loginMethod").isEqualTo("GOOGLE");
		});
	}

	@Test
	void socialAccountWithoutNameHasNullName() {
		long userId = insertSocialUser("APPLE", null);

		assertThat(me(userId)).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.name").isNull();
			assertThat(json).extractingPath("$.loginMethod").isEqualTo("APPLE");
		});
	}

	@Test
	void needsLogin() {
		assertThat(mvc.get().uri("/api/users/me")).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
		assertThat(mvc.delete().uri("/api/users/me")).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	@Test
	void withdrawnUserIsUnauthorized() {
		long userId = insertLocalUser("gone_01", "탈퇴 예정");

		assertThat(withdraw(userId)).hasStatus(HttpStatus.NO_CONTENT).body().isEmpty();
		flush();

		assertThat(me(userId)).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code")
				.isEqualTo("UNAUTHORIZED");
		assertThat(withdraw(userId)).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code")
				.isEqualTo("UNAUTHORIZED");
	}

	@Test
	void withdrawingDeletesEverythingOfTheUserOnly() {
		long userId = insertSocialUser("GOOGLE", "Sato Yui");
		long otherId = insertLocalUser("stay_01", "남는 사용자");
		long placeId = jdbc.queryForObject("SELECT min(id) FROM places", Long.class);
		for (long id : new long[] {userId, otherId}) {
			jdbc.update("INSERT INTO favorites (user_id, place_id, created_at) VALUES (?, ?, now())", id, placeId);
			jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at) "
					+ "VALUES (?, ?, now() + interval '14 days', now())", id, "h".repeat(63) + id % 10);
			insertTrip(id);
		}
		flush();

		assertThat(withdraw(userId)).hasStatus(HttpStatus.NO_CONTENT);
		flush();

		for (String table : new String[] {"favorites", "refresh_tokens", "oauth_accounts", "trips"}) {
			assertThat(count("SELECT count(*) FROM " + table + " WHERE user_id = ?", userId)).as(table).isZero();
		}
		assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isZero();
		for (String table : new String[] {"favorites", "refresh_tokens", "trips"}) {
			assertThat(count("SELECT count(*) FROM " + table + " WHERE user_id = ?", otherId)).as(table).isOne();
		}
		assertThat(count("SELECT count(*) FROM users WHERE id = ?", otherId)).isOne();
	}

	private MvcTestResult me(long userId) {
		return mvc.get().uri("/api/users/me").header("X-Dev-User-Id", userId).exchange();
	}

	private MvcTestResult withdraw(long userId) {
		return mvc.delete().uri("/api/users/me").header("X-Dev-User-Id", userId).exchange();
	}

	private void insertTrip(long ownerId) {
		jdbc.update("""
				INSERT INTO trips (user_id, status, nationality_code, start_date, end_date, party_size,
				                   stay_type, stay_area_code, stay_name, stay_location, created_at, updated_at)
				VALUES (?, 'DRAFT', 'JP', '2026-11-10', '2026-11-12', 2,
				        'AREA', 'MYEONGDONG', '명동', ST_GeogFromText('SRID=4326;POINT(126.9849 37.5621)'), now(), now())
				""", ownerId);
	}

	private long insertLocalUser(String loginId, String name) {
		return jdbc.queryForObject("""
				INSERT INTO users (login_id, password_hash, name, created_at, updated_at)
				VALUES (?, 'x', ?, now(), now()) RETURNING id
				""", Long.class, loginId, name);
	}

	private long insertSocialUser(String provider, String name) {
		long userId = jdbc.queryForObject(
				"INSERT INTO users (name, created_at, updated_at) VALUES (?, now(), now()) RETURNING id", Long.class, name);
		jdbc.update("INSERT INTO oauth_accounts (user_id, provider, subject, created_at) VALUES (?, ?, ?, now())",
				userId, provider, "sub-" + userId);
		return userId;
	}

	private long count(String sql, long id) {
		return jdbc.queryForObject(sql, Long.class, id);
	}

	/** 쓰기를 DB에 보내고 읽은 엔티티를 비운다 (JPQL 삭제나 SQL 삽입 뒤 다시 읽게) */
	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}
}
