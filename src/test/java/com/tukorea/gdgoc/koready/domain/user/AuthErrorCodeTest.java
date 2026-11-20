package com.tukorea.gdgoc.koready.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.security.JwtProvider;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 이슈 #23: 인증 API 오류 code가 API 명세의 에러 코드 표와 같은지 */
@IntegrationTest
class AuthErrorCodeTest {

	@Autowired
	MockMvcTester mvc;

	@Value("${jwt.secret}")
	String jwtSecret;

	@Test
	void wrongPasswordIsLoginFailed() {
		signup("errcode_test01");

		assertThat(post("/api/auth/login", """
				{"loginId": "errcode_test01", "password": "wrong-password"}
				"""))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
		assertThat(post("/api/auth/login", """
				{"loginId": "no_such_user", "password": "password1234"}
				"""))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
	}

	@Test
	void unknownRefreshTokenIsInvalidRefreshToken() {
		assertThat(post("/api/auth/refresh", "{\"refreshToken\": \"unknown-token\"}"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("INVALID_REFRESH_TOKEN");
	}

	@Test
	void duplicateLoginIdIsLoginIdTaken() {
		signup("errcode_test02");

		assertThat(post("/api/auth/signup", signupBody("errcode_test02")))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson().extractingPath("$.code").isEqualTo("LOGIN_ID_TAKEN");
	}

	@Test
	void loginIdRuleAllowsOnlyLowercaseDigitsUnderscore() {
		assertThat(post("/api/auth/signup", signupBody("has-hyphen")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "loginId"}]}
						""");
		assertThat(mvc.get().uri("/api/auth/login-id/availability?loginId=has-hyphen"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
		assertThat(mvc.get().uri("/api/auth/login-id/availability?loginId=free_id_01"))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"loginId": "free_id_01", "available": true}
						""");
	}

	@Test
	void expiredAccessTokenIsTokenExpired() {
		JwtProvider past = new JwtProvider(jwtSecret, 30, 14, Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
		String expired = past.createAccessToken(1L);

		assertThat(mvc.get().uri("/api/trips/1").header("Authorization", "Bearer " + expired))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("TOKEN_EXPIRED");
		assertThat(mvc.get().uri("/api/trips/1").header("Authorization", "Bearer not-a-jwt"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
		assertThat(mvc.get().uri("/api/trips/1"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	private void signup(String loginId) {
		assertThat(post("/api/auth/signup", signupBody(loginId))).hasStatus(HttpStatus.CREATED);
	}

	private static String signupBody(String loginId) {
		return """
				{"name": "테스트", "loginId": "%s", "password": "password1234"}
				""".formatted(loginId);
	}

	private MvcTestResult post(String uri, String body) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}
}
