package com.tukorea.gdgoc.koready.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class LogoutTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void logoutRevokesRefreshToken() {
		String refreshToken = signupAndLogin("logout_test01");

		assertThat(post("/api/auth/logout", refreshBody(refreshToken)))
				.hasStatus(HttpStatus.NO_CONTENT);

		// 로그아웃한 토큰으로는 재발급할 수 없다
		assertThat(post("/api/auth/refresh", refreshBody(refreshToken)))
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void logoutIsIdempotent() {
		String refreshToken = signupAndLogin("logout_test02");

		assertThat(post("/api/auth/logout", refreshBody(refreshToken))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(post("/api/auth/logout", refreshBody(refreshToken))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(post("/api/auth/logout", refreshBody("unknown-token"))).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutOnlyRevokesThatDevice() {
		String deviceA = signupAndLogin("logout_test03");
		String deviceB = login("logout_test03");

		assertThat(post("/api/auth/logout", refreshBody(deviceA))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(post("/api/auth/refresh", refreshBody(deviceB))).hasStatusOk();
	}

	@Test
	void blankRefreshTokenIsInvalidRequest() {
		assertThat(post("/api/auth/logout", "{\"refreshToken\": \" \"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "refreshToken"}]}
						""");
	}

	private String signupAndLogin(String loginId) {
		assertThat(post("/api/auth/signup", """
				{"name": "테스트", "loginId": "%s", "password": "password1234"}
				""".formatted(loginId)))
				.hasStatus(HttpStatus.CREATED);
		return login(loginId);
	}

	private String login(String loginId) {
		MvcTestResult result = post("/api/auth/login", """
				{"loginId": "%s", "password": "password1234"}
				""".formatted(loginId));
		assertThat(result).hasStatusOk();
		try {
			return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.refreshToken");
		} catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private MvcTestResult post(String uri, String body) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\": \"%s\"}".formatted(refreshToken);
	}
}
