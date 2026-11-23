package com.tukorea.gdgoc.koready.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * 회원가입·로그인·소셜 로그인·토큰 재발급 응답 (API 명세 공통 객체 AuthTokens).
 * 만료 시각은 서울 시각(+09:00)이다. newUser는 소셜 로그인 응답에만 있다 (다른 응답에서는 빠진다).
 */
public record TokenResponse(
		String accessToken,
		OffsetDateTime accessTokenExpiresAt,
		String refreshToken,
		OffsetDateTime refreshTokenExpiresAt,
		String tokenType,
		UserSummary user,
		@JsonInclude(JsonInclude.Include.NON_NULL) Boolean newUser) {

	public static final String TOKEN_TYPE = "Bearer";

	/** 사용자 요약 (API 명세 공통 객체 UserSummary). loginMethod: ID·GOOGLE·APPLE, name은 없을 수 있다 */
	public record UserSummary(Long userId, String name, String loginMethod) {
	}

	/** 소셜 로그인: 이번에 계정을 만들었는지 더한다 */
	public TokenResponse withNewUser(boolean value) {
		return new TokenResponse(accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt, tokenType, user,
				value);
	}
}
