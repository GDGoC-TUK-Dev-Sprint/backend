package com.tukorea.gdgoc.koready.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank @Size(max = 50) String name,
		@NotBlank @Pattern(regexp = "^[a-z0-9_-]{4,30}$",
				message = "아이디는 영문 소문자·숫자·-_ 4~30자입니다") String loginId,
		@NotBlank @Size(min = 8, max = 64) String password
) {}
