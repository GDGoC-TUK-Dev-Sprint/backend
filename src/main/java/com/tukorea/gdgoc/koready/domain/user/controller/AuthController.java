package com.tukorea.gdgoc.koready.domain.user.controller;

import com.tukorea.gdgoc.koready.domain.user.dto.LoginRequest;
import com.tukorea.gdgoc.koready.domain.user.dto.OauthLoginRequest;
import com.tukorea.gdgoc.koready.domain.user.dto.RefreshRequest;
import com.tukorea.gdgoc.koready.domain.user.dto.SignupRequest;
import com.tukorea.gdgoc.koready.domain.user.dto.TokenResponse;
import com.tukorea.gdgoc.koready.domain.user.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	/** 회원가입-06 */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public void signup(@Valid @RequestBody SignupRequest req) {
		authService.signup(req);
	}

	/** 회원가입-07 아이디 중복 확인 */
	@GetMapping("/login-id/availability")
	public Map<String, Boolean> availability(@RequestParam String loginId) {
		return Map.of("available", authService.isLoginIdAvailable(loginId));
	}

	/** 로그인-01 */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest req) {
		return authService.login(req);
	}

	/** 로그인-03·04 (provider: google | apple) */
	@PostMapping("/oauth/{provider}")
	public TokenResponse oauth(@PathVariable String provider, @Valid @RequestBody OauthLoginRequest req) {
		return authService.oauthLogin(provider, req);
	}

	/** 토큰 재발급 (명세에 경로가 없어 임의로 정함 → 프론트와 합의 필요) */
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
		return authService.refresh(req.refreshToken());
	}
}
