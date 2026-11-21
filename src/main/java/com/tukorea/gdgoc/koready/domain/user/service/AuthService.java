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
import com.tukorea.gdgoc.koready.global.util.SeoulTime;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
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

	/** 아이디·비밀번호 계정의 로그인 수단 (UserSummary.loginMethod) */
	private static final String LOGIN_METHOD_ID = "ID";

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

	/** 회원가입 (API 명세 1). 가입하면 바로 로그인 상태가 되도록 토큰 묶음을 준다 */
	@Transactional
	public TokenResponse signup(SignupRequest req) {
		if (tooLongForBcrypt(req.password())) {
			throw AuthErrors.passwordTooLong();
		}
		if (userRepository.existsByLoginId(req.loginId())) {
			throw AuthErrors.duplicatedLoginId();
		}
		User user;
		try {
			user = userRepository.saveAndFlush(User.ofLocal(
					req.loginId(), passwordEncoder.encode(req.password()), req.name().trim()));
		} catch (DataIntegrityViolationException e) {
			throw AuthErrors.duplicatedLoginId(); // 동시 가입으로 unique 제약에 걸린 경우
		}
		return issueTokens(user, LOGIN_METHOD_ID);
	}

	/** 로그인 (API 명세 2). 그 사용자의 만료된 리프레시 토큰은 이때 지운다 */
	@Transactional
	public TokenResponse login(LoginRequest req) {
		if (tooLongForBcrypt(req.password())) {
			throw AuthErrors.loginFailed();
		}
		User user = userRepository.findByLoginId(req.loginId())
				.filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
				.orElseThrow(AuthErrors::loginFailed);
		refreshTokenRepository.deleteExpiredByUserId(user.getId(), clock.instant());
		return issueTokens(user, LOGIN_METHOD_ID);
	}

	/** 소셜 로그인 (API 명세 3). 처음 로그인하면 계정을 만들고 newUser = true */
	@Transactional
	public TokenResponse oauthLogin(String providerName, OauthLoginRequest req) {
		OauthProvider provider = parseProvider(providerName);
		OauthIdTokenVerifier.Identity identity = oauthIdTokenVerifier.verify(provider, req.idToken());

		Optional<OauthAccount> account = oauthAccountRepository.findByProviderAndSubject(provider, identity.subject());
		boolean newUser = account.isEmpty();
		User user = account.map(OauthAccount::getUser)
				.orElseGet(() -> createSocialUser(provider, identity, req.name()));
		if (!newUser) {
			refreshTokenRepository.deleteExpiredByUserId(user.getId(), clock.instant());
		}
		return issueTokens(user, provider.name()).withNewUser(newUser);
	}

	/** 리프레시 토큰을 쓰면 새 토큰으로 교체한다(기존 토큰은 폐기). */
	@Transactional
	public TokenResponse refresh(String rawRefreshToken) {
		RefreshToken saved = refreshTokenRepository.findByTokenHash(jwtProvider.hash(rawRefreshToken))
				.orElseThrow(AuthErrors::invalidRefreshToken);
		if (!saved.getExpiresAt().isAfter(clock.instant())) {
			throw AuthErrors.invalidRefreshToken();
		}
		User user = saved.getUser();
		refreshTokenRepository.delete(saved);
		return issueTokens(user, loginMethod(user));
	}

	/** 로그아웃: 이 기기의 리프레시 토큰을 폐기한다. 없는 토큰이어도 오류 없이 끝난다(멱등). */
	@Transactional
	public void logout(String rawRefreshToken) {
		refreshTokenRepository.deleteByTokenHash(jwtProvider.hash(rawRefreshToken));
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

	/** 토큰 묶음 (API 명세 AuthTokens). 리프레시 토큰은 해시만 저장한다 */
	private TokenResponse issueTokens(User user, String loginMethod) {
		String refreshToken = jwtProvider.newRefreshTokenValue();
		Instant refreshExpiresAt = jwtProvider.refreshExpiresAt();
		refreshTokenRepository.save(RefreshToken.of(user, jwtProvider.hash(refreshToken), refreshExpiresAt));
		String accessToken = jwtProvider.createAccessToken(user.getId());
		return new TokenResponse(
				accessToken,
				SeoulTime.of(jwtProvider.accessExpiresAt()),
				refreshToken,
				SeoulTime.of(refreshExpiresAt),
				TokenResponse.TOKEN_TYPE,
				new TokenResponse.UserSummary(user.getId(), user.getName(), loginMethod),
				null);
	}

	/** 재발급할 때의 로그인 수단: 아이디 계정이면 ID, 아니면 연결된 소셜 계정의 제공자 */
	private String loginMethod(User user) {
		if (user.getLoginId() != null) {
			return LOGIN_METHOD_ID;
		}
		return oauthAccountRepository.findFirstByUserIdOrderByIdAsc(user.getId())
				.map(account -> account.getProvider().name())
				.orElse(LOGIN_METHOD_ID);
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
