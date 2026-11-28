package com.tukorea.gdgoc.koready.domain.user.controller;

import com.tukorea.gdgoc.koready.domain.user.dto.MyInfoResponse;
import com.tukorea.gdgoc.koready.domain.user.service.UserService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class UserController {

	private final UserService userService;

	/** 내 정보: 마이페이지 프로필(이름·로그인 수단). 앱을 시작할 때 저장한 토큰이 아직 쓸 만한지 확인하는 데도 쓴다 */
	@GetMapping("/api/users/me")
	MyInfoResponse me(@LoginUserId Long userId) {
		return userService.me(userId);
	}

	/** 회원 탈퇴: 그 사용자의 소셜 계정·리프레시 토큰·찜·여행을 모두 지운다. 확인 창은 앱에서 띄운다 */
	@DeleteMapping("/api/users/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@LoginUserId Long userId) {
		userService.withdraw(userId);
	}
}
