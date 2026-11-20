package com.tukorea.gdgoc.koready.domain.user.service;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorBody;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 인증 기능의 에러를 한곳에서 만든다. 응답 code는 API 명세의 에러 코드 표를 따른다.
 */
final class AuthErrors {

	private AuthErrors() {}

	/** 401 LOGIN_FAILED: 아이디·비밀번호가 맞지 않음 (무엇이 틀렸는지 구분하지 않는다) */
	static ApiException loginFailed() {
		return new ApiException(ErrorCode.LOGIN_FAILED);
	}

	/** 401 INVALID_REFRESH_TOKEN: 리프레시 토큰이 없거나 만료됐거나 이미 씀 (앱은 로그인 화면으로 간다) */
	static ApiException invalidRefreshToken() {
		return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
	}

	/** 409 LOGIN_ID_TAKEN: 이미 쓰는 아이디 */
	static ApiException duplicatedLoginId() {
		return new ApiException(ErrorCode.LOGIN_ID_TAKEN);
	}

	/** 400 INVALID_REQUEST (fields: password): BCrypt가 처리할 수 있는 길이를 넘음 */
	static ApiException passwordTooLong() {
		return ApiException.invalid(List.of(new ErrorBody.Field("password", "비밀번호가 너무 깁니다")));
	}

	/** 400 INVALID_REQUEST (fields: provider): google·apple이 아님 */
	static ApiException unsupportedProvider() {
		return ApiException.invalid(List.of(new ErrorBody.Field("provider", "google 또는 apple이어야 합니다")));
	}

	/** 같은 소셜 계정으로 동시에 처음 로그인한 경우. 명세 에러 코드 표에 없어 팀 확인 필요 */
	static ResponseStatusException retryLater() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "잠시 후 다시 시도해주세요");
	}
}
