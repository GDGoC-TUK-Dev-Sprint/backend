package com.tukorea.gdgoc.koready.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** name은 선택값: Apple은 ID 토큰에 이름이 없어서 앱이 첫 로그인 때 받은 이름을 넘길 수 있게 함 */
public record OauthLoginRequest(@NotBlank String idToken, @Size(max = 50) String name) {}
