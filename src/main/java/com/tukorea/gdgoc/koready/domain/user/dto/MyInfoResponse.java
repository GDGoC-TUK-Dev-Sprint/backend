package com.tukorea.gdgoc.koready.domain.user.dto;

/**
 * 내 정보 (API 명세 6, 마이페이지-01). loginMethod는 ID·GOOGLE·APPLE.
 *
 * @param name 소셜 계정은 제공자가 이름을 주지 않았으면 null
 * @param loginId 아이디 계정만 값이 있다
 */
public record MyInfoResponse(Long userId, String name, String loginId, String loginMethod) {
}
