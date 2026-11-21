package com.tukorea.gdgoc.koready.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** API 명세 AuthTokens: 회원가입·로그인 응답 형태 */
@IntegrationTest
class AuthTokensTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void signupReturnsAuthTokens() {
		assertThat(post("/api/auth/signup", """
				{"name": "토큰 테스트", "loginId": "tokens_test01", "password": "password1234"}
				"""))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"tokenType": "Bearer", "user": {"name": "토큰 테스트", "loginMethod": "ID"}}
						""");
	}

	@Test
	void loginReturnsAuthTokensWithExpiry() {
		post("/api/auth/signup", """
				{"name": "토큰 테스트", "loginId": "tokens_test02", "password": "password1234"}
				""");

		MvcTestResult login = post("/api/auth/login", """
				{"loginId": "tokens_test02", "password": "password1234"}
				""");

		assertThat(login).hasStatusOk();
		assertThat(login).bodyJson().extractingPath("$.accessToken").isNotNull();
		assertThat(login).bodyJson().extractingPath("$.accessTokenExpiresAt").isNotNull();
		assertThat(login).bodyJson().extractingPath("$.refreshTokenExpiresAt").isNotNull();
		assertThat(login).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
		assertThat(login).bodyJson().extractingPath("$.user.loginMethod").isEqualTo("ID");
		assertThat(login).bodyJson().doesNotHavePath("$.newUser");
	}

	private MvcTestResult post(String uri, String body) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}
}
