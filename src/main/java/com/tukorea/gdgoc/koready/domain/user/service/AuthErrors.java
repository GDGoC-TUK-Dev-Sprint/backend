package com.tukorea.gdgoc.koready.domain.user.service;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 인증 기능의 에러를 한곳에서 만든다.
 * 팀의 ErrorCode에 맞는 항목(중복 아이디 등)이 생기면 이 파일만 고치면 된다.
 */
final class AuthErrors {

	private AuthErrors() {}

	static ApiException unauthorized() {
		return new ApiException(ErrorCode.UNAUTHORIZED);
	}

	static ResponseStatusException duplicatedLoginId() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다");
	}

	static ResponseStatusException passwordTooLong() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 너무 깁니다");
	}

	static ResponseStatusException unsupportedProvider() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다");
	}

	static ResponseStatusException retryLater() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "잠시 후 다시 시도해주세요");
	}
}
