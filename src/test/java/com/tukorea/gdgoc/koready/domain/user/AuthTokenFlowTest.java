package com.tukorea.gdgoc.koready.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import com.tukorea.gdgoc.koready.domain.user.entity.OauthProvider;
import com.tukorea.gdgoc.koready.domain.user.entity.RefreshToken;
import com.tukorea.gdgoc.koready.domain.user.entity.User;
import com.tukorea.gdgoc.koready.domain.user.repository.RefreshTokenRepository;
import com.tukorea.gdgoc.koready.domain.user.repository.UserRepository;
import com.tukorea.gdgoc.koready.global.auth.OauthIdTokenVerifier;
import com.tukorea.gdgoc.koready.security.JwtProvider;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 소셜 로그인 newUser, 재발급 응답, 만료 시각, 로그인 때 만료 토큰 정리 (API 명세 2~4) */
@IntegrationTest
class AuthTokenFlowTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	Clock clock;

	@MockitoBean
	OauthIdTokenVerifier oauthIdTokenVerifier;

	@Test
	void oauthLoginTellsNewUserOnlyOnFirstLogin() {
		given(oauthIdTokenVerifier.verify(eq(OauthProvider.GOOGLE), anyString()))
				.willReturn(new OauthIdTokenVerifier.Identity("flow-sub-01", "소셜 사용자"));

		assertThat(post("/api/auth/oauth/google", "{\"idToken\": \"token\"}"))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"newUser": true, "tokenType": "Bearer", "user": {"name": "소셜 사용자", "loginMethod": "GOOGLE"}}
						""");
		assertThat(post("/api/auth/oauth/google", "{\"idToken\": \"token\"}"))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"newUser": false, "user": {"loginMethod": "GOOGLE"}}
						""");
	}

	@Test
	void refreshKeepsLoginMethodOfSocialUser() {
		given(oauthIdTokenVerifier.verify(eq(OauthProvider.APPLE), anyString()))
				.willReturn(new OauthIdTokenVerifier.Identity("flow-sub-02", null));
		String refreshToken = read(post("/api/auth/oauth/apple", "{\"idToken\": \"token\"}"), "$.refreshToken");

		MvcTestResult refreshed = post("/api/auth/refresh", refreshBody(refreshToken));

		assertThat(refreshed).hasStatusOk();
		assertThat(refreshed).bodyJson().extractingPath("$.user.loginMethod").isEqualTo("APPLE");
		assertThat(refreshed).bodyJson().doesNotHavePath("$.newUser");
	}

	@Test
	void refreshRotatesTokenAndUsedTokenIsInvalid() {
		signup("flow_test01");
		String refreshToken = read(login("flow_test01"), "$.refreshToken");

		MvcTestResult refreshed = post("/api/auth/refresh", refreshBody(refreshToken));

		assertThat(refreshed).hasStatusOk();
		assertThat(refreshed).bodyJson().extractingPath("$.user.loginMethod").isEqualTo("ID");
		assertThat(post("/api/auth/refresh", refreshBody(refreshToken)))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.code").isEqualTo("INVALID_REFRESH_TOKEN");
	}

	@Test
	void expiresAtMatchesTokenAndHasNoFraction() throws ParseException {
		signup("flow_test02");

		MvcTestResult login = login("flow_test02");

		// 테스트 시계: 2026-11-01 10:00 (액세스 30분, 리프레시 14일)
		assertThat(login).bodyJson().extractingPath("$.accessTokenExpiresAt").isEqualTo("2026-11-01T10:30:00+09:00");
		assertThat(login).bodyJson().extractingPath("$.refreshTokenExpiresAt").isEqualTo("2026-11-15T10:00:00+09:00");
		String accessToken = read(login, "$.accessToken");
		String expiresAt = read(login, "$.accessTokenExpiresAt");
		assertThat(SignedJWT.parse(accessToken).getJWTClaimsSet().getExpirationTime().toInstant())
				.isEqualTo(OffsetDateTime.parse(expiresAt).toInstant());
	}

	@Test
	void loginRemovesOnlyExpiredRefreshTokensOfThatUser() {
		signup("flow_test03");
		User user = userRepository.findByLoginId("flow_test03").orElseThrow();
		String expiredHash = jwtProvider.hash("expired-token");
		String validHash = jwtProvider.hash("valid-token");
		refreshTokenRepository.save(RefreshToken.of(user, expiredHash, clock.instant().minus(Duration.ofSeconds(1))));
		refreshTokenRepository.save(RefreshToken.of(user, validHash, clock.instant().plus(Duration.ofDays(1))));

		assertThat(login("flow_test03")).hasStatusOk();

		assertThat(refreshTokenRepository.findByTokenHash(expiredHash)).isEmpty();
		assertThat(refreshTokenRepository.findByTokenHash(validHash)).isPresent();
	}

	private void signup(String loginId) {
		assertThat(post("/api/auth/signup", """
				{"name": "테스트", "loginId": "%s", "password": "password1234"}
				""".formatted(loginId)))
				.hasStatus(HttpStatus.CREATED);
	}

	private MvcTestResult login(String loginId) {
		MvcTestResult result = post("/api/auth/login", """
				{"loginId": "%s", "password": "password1234"}
				""".formatted(loginId));
		assertThat(result).hasStatusOk();
		return result;
	}

	private MvcTestResult post(String uri, String body) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private static String read(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\": \"%s\"}".formatted(refreshToken);
	}
}
