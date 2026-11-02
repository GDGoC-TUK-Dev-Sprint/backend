package com.tukorea.gdgoc.koready.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Security는 요청을 막지 않고, 로그인 확인은 {@code @LoginUserId}가 한다.
 * 로그인이 필요한 API는 회원가입·로그인으로 받은 액세스 토큰(Bearer)으로 부른다.
 */
@IntegrationTest
class SecurityConfigTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	EntityManager entityManager;

	@Test
	void publicApisNeedNoLogin() {
		assertThat(mvc.get().uri("/api/meta/options")).hasStatusOk();
		assertThat(mvc.get().uri("/api/lodgings").param("query", "명동")).hasStatusOk();
	}

	@Test
	void accessTokenFromLoginCallsTripApis() throws Exception {
		// 로컬 서버에서 손으로 가입한 아이디와 겹치지 않게 실행마다 다른 아이디
		String loginId = "security-" + Long.toString(System.nanoTime(), 36);
		String authorization = "Bearer " + signUpAndLogIn(loginId, "password1234");

		MvcTestResult created = mvc.post().uri("/api/trips")
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nationality": "JP", "startDate": "2026-11-10", "endDate": "2026-11-12", "partySize": 2,
						 "stay": {"type": "AREA", "areaCode": "MYEONGDONG"}}
						""")
				.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		entityManager.flush();
		entityManager.clear();
		String body = created.getResponse().getContentAsString(StandardCharsets.UTF_8);
		long tripId = JsonPath.<Number>read(body, "$.tripId").longValue();

		assertThat(mvc.get().uri("/api/trips/{tripId}", tripId).header("Authorization", authorization))
				.hasStatusOk()
				.bodyJson()
				.isStrictlyEqualTo(body);
	}

	@Test
	void loginRequiredApiWithoutValidTokenIsUnauthorized() {
		assertThat(mvc.get().uri("/api/trips/1"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "UNAUTHORIZED", "fields": []}
						""");
		assertThat(mvc.get().uri("/api/trips/1").header("Authorization", "Bearer not-a-token"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.extractingPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	/** 회원가입하고 로그인해 액세스 토큰을 받는다 */
	private String signUpAndLogIn(String loginId, String password) throws Exception {
		assertThat(mvc.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "시험 사용자", "loginId": "%s", "password": "%s"}
				""".formatted(loginId, password)))
				.hasStatus(HttpStatus.CREATED);
		MvcTestResult login = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"loginId": "%s", "password": "%s"}
				""".formatted(loginId, password)).exchange();
		assertThat(login).hasStatusOk();
		return JsonPath.read(login.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
	}
}
