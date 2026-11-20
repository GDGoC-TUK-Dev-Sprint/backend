package com.tukorea.gdgoc.koready.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank @Size(max = 50) String name,
		@NotBlank @Pattern(regexp = SignupRequest.LOGIN_ID_REGEX,
				message = SignupRequest.LOGIN_ID_MESSAGE) String loginId,
		@NotBlank @Size(min = 8, max = 64) String password
) {

	/** 아이디 규칙 (API 명세·Figma: 영문 소문자·숫자·_ 4~30자). 아이디 중복 확인도 같은 규칙으로 검사한다 */
	public static final String LOGIN_ID_REGEX = "^[a-z0-9_]{4,30}$";
	public static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자·숫자·_ 4~30자입니다";
}
