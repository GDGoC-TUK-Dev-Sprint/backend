package com.tukorea.gdgoc.koready.domain.user.service;

import com.tukorea.gdgoc.koready.domain.user.dto.LoginRequest;
import com.tukorea.gdgoc.koready.domain.user.dto.OauthLoginRequest;
import com.tukorea.gdgoc.koready.domain.user.dto.SignupRequest;
import com.tukorea.gdgoc.koready.domain.user.dto.TokenResponse;
import com.tukorea.gdgoc.koready.domain.user.entity.OauthAccount;
import com.tukorea.gdgoc.koready.domain.user.entity.OauthProvider;
import com.tukorea.gdgoc.koready.domain.user.entity.RefreshToken;
import com.tukorea.gdgoc.koready.domain.user.entity.User;
import com.tukorea.gdgoc.koready.domain.user.repository.OauthAccountRepository;
import com.tukorea.gdgoc.koready.domain.user.repository.RefreshTokenRepository;
import com.tukorea.gdgoc.koready.domain.user.repository.UserRepository;
import com.tukorea.gdgoc.koready.security.JwtProvider;
import com.tukorea.gdgoc.koready.global.auth.OauthIdTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	/** BCrypt는 72바이트까지만 처리한다 (한글은 글자당 3바이트) */
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;
	private final OauthAccountRepository oauthAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final OauthIdTokenVerifier oauthIdTokenVerifier;
	private final Clock clock;

	@Transactional(readOnly = true)
	public boolean isLoginIdAvailable(String loginId) {
		return !userRepository.existsByLoginId(loginId);
	}

	@Transactional
	public void signup(SignupRequest req) {
		if (tooLongForBcrypt(req.password())) {
			throw AuthErrors.passwordTooLong();
		}
		if (userRepository.existsByLoginId(req.loginId())) {
			throw AuthErrors.duplicatedLoginId();
		}
		try {
			userRepository.saveAndFlush(User.ofLocal(
					req.loginId(), passwordEncoder.encode(req.password()), req.name().trim()));
		} catch (DataIntegrityViolationException e) {
			throw AuthErrors.duplicatedLoginId(); // 동시 가입으로 unique 제약에 걸린 경우
		}
	}

	@Transactional
	public TokenResponse login(LoginRequest req) {
		if (tooLongForBcrypt(req.password())) {
			throw AuthErrors.unauthorized();
		}
		User user = userRepository.findByLoginId(req.loginId())
				.filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
				.orElseThrow(AuthErrors::unauthorized);
		return issueTokens(user);
	}

	@Transactional
	public TokenResponse oauthLogin(String providerName, OauthLoginRequest req) {
		OauthProvider provider = parseProvider(providerName);
		OauthIdTokenVerifier.Identity identity = oauthIdTokenVerifier.verify(provider, req.idToken());

		User user = oauthAccountRepository.findByProviderAndSubject(provider, identity.subject())
				.map(OauthAccount::getUser)
				.orElseGet(() -> createSocialUser(provider, identity, req.name()));
		return issueTokens(user);
	}

	/** 리프레시 토큰을 쓰면 새 토큰으로 교체한다(기존 토큰은 폐기). */
	@Transactional
	public TokenResponse refresh(String rawRefreshToken) {
		RefreshToken saved = refreshTokenRepository.findByTokenHash(jwtProvider.hash(rawRefreshToken))
				.orElseThrow(AuthErrors::unauthorized);
		if (!saved.getExpiresAt().isAfter(clock.instant())) {
			throw AuthErrors.unauthorized();
		}
		User user = saved.getUser();
		refreshTokenRepository.delete(saved);
		return issueTokens(user);
	}

	private User createSocialUser(OauthProvider provider, OauthIdTokenVerifier.Identity identity,
			String requestName) {
		String name = firstNonBlank(identity.name(), requestName);
		if (name != null && name.length() > 50) {
			name = name.substring(0, 50);
		}
		try {
			User user = userRepository.save(User.ofSocial(name));
			oauthAccountRepository.saveAndFlush(OauthAccount.of(user, provider, identity.subject()));
			return user;
		} catch (DataIntegrityViolationException e) {
			throw AuthErrors.retryLater(); // 같은 계정으로 동시에 첫 로그인한 경우
		}
	}

	private TokenResponse issueTokens(User user) {
		String refreshToken = jwtProvider.newRefreshTokenValue();
		refreshTokenRepository.save(RefreshToken.of(
				user, jwtProvider.hash(refreshToken), jwtProvider.refreshExpiresAt()));
		return new TokenResponse(jwtProvider.createAccessToken(user.getId()), refreshToken);
	}

	private OauthProvider parseProvider(String name) {
		try {
			return OauthProvider.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw AuthErrors.unsupportedProvider();
		}
	}

	private static boolean tooLongForBcrypt(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
	}

	private static String firstNonBlank(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first.trim();
		}
		if (second != null && !second.isBlank()) {
			return second.trim();
		}
		return null;
	}
}
