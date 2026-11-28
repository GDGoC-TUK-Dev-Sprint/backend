package com.tukorea.gdgoc.koready.domain.user.service;

import com.tukorea.gdgoc.koready.domain.user.dto.MyInfoResponse;
import com.tukorea.gdgoc.koready.domain.user.entity.User;
import com.tukorea.gdgoc.koready.domain.user.repository.OauthAccountRepository;
import com.tukorea.gdgoc.koready.domain.user.repository.UserRepository;
import com.tukorea.gdgoc.koready.domain.user.repository.UserWithdrawalRepository;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지의 내 정보 조회(API 명세 6)와 회원 탈퇴(7).
 */
@Service
@RequiredArgsConstructor
public class UserService {

	/** 아이디·비밀번호 계정의 로그인 수단 (UserSummary.loginMethod와 같은 값) */
	private static final String LOGIN_METHOD_ID = "ID";

	private final UserRepository users;
	private final OauthAccountRepository oauthAccounts;
	private final UserWithdrawalRepository withdrawals;

	/**
	 * 내 정보. 앱을 시작할 때 토큰 확인에도 쓴다.
	 *
	 * @throws ApiException 탈퇴해 사용자가 없으면 {@code 401 UNAUTHORIZED}
	 */
	@Transactional(readOnly = true)
	public MyInfoResponse me(Long userId) {
		User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
		return new MyInfoResponse(user.getId(), user.getName(), user.getLoginId(), loginMethod(user));
	}

	/**
	 * 회원 탈퇴. 사용자 행을 지우면 딸린 데이터는 DB가 함께 지운다.
	 * 만료 전 액세스 토큰으로 다시 부르면 사용자가 없으므로 {@code 401 UNAUTHORIZED}다.
	 */
	@Transactional
	public void withdraw(Long userId) {
		if (withdrawals.deleteUserById(userId) == 0) {
			throw new ApiException(ErrorCode.UNAUTHORIZED);
		}
	}

	/** 아이디 계정이면 ID, 아니면 연결된 소셜 계정의 제공자 */
	private String loginMethod(User user) {
		if (user.getLoginId() != null) {
			return LOGIN_METHOD_ID;
		}
		return oauthAccounts.findFirstByUserIdOrderByIdAsc(user.getId())
				.map(account -> account.getProvider().name())
				.orElse(LOGIN_METHOD_ID);
	}
}
