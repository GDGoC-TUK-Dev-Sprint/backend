package com.tukorea.gdgoc.koready.global.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(JWT) 발급·검증, 리프레시 토큰 값 생성·해시.
 * 리프레시 토큰은 JWT가 아니라 무작위 문자열이며, DB에는 SHA-256 해시만 저장한다.
 */
@Component
public class JwtProvider {

	private final byte[] secret;
	private final long accessMinutes;
	private final long refreshDays;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public JwtProvider(@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-exp-min:30}") long accessMinutes,
			@Value("${jwt.refresh-exp-days:14}") long refreshDays,
			Clock clock) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("jwt.secret은 32바이트(영문 32자) 이상이어야 합니다");
		}
		this.secret = bytes;
		this.accessMinutes = accessMinutes;
		this.refreshDays = refreshDays;
		this.clock = clock;
	}

	public String createAccessToken(Long userId) {
		Instant now = clock.instant();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(String.valueOf(userId))
				.claim("type", "access")
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(Duration.ofMinutes(accessMinutes))))
				.build();
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			jwt.sign(new MACSigner(secret));
			return jwt.serialize();
		} catch (JOSEException e) {
			throw new IllegalStateException("액세스 토큰 생성에 실패했습니다", e);
		}
	}

	/** 유효한 액세스 토큰이면 사용자 ID를, 아니면 빈 값을 돌려준다. */
	public Optional<Long> parseAccessToken(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!jwt.verify(new MACVerifier(secret))) {
				return Optional.empty();
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date expiration = claims.getExpirationTime();
			if (expiration == null || !expiration.toInstant().isAfter(clock.instant())
					|| !"access".equals(claims.getStringClaim("type"))) {
				return Optional.empty();
			}
			return Optional.of(Long.valueOf(claims.getSubject()));
		} catch (ParseException | JOSEException | NumberFormatException e) {
			return Optional.empty();
		}
	}

	public String newRefreshTokenValue() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 16진수 64자 (refresh_tokens.token_hash) */
	public String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public Instant refreshExpiresAt() {
		return clock.instant().plus(Duration.ofDays(refreshDays));
	}
}
