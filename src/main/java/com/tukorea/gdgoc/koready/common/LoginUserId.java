package com.tukorea.gdgoc.koready.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 사용자 ID를 컨트롤러 파라미터로 받는다: {@code @LoginUserId Long userId}.
 * 값은 요청 속성 {@link LoginUserIdArgumentResolver#REQUEST_ATTRIBUTE}에서 읽고, 없으면 {@code 401 UNAUTHORIZED}다.
 * 인증 필터가 토큰을 확인한 뒤 이 속성을 채운다 (local·test 프로필에서는 {@link DevLoginUserFilter}가 개발용 헤더로 채운다).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUserId {
}
