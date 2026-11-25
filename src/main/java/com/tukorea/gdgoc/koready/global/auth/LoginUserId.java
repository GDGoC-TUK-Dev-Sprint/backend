package com.tukorea.gdgoc.koready.global.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 사용자 ID를 컨트롤러 파라미터로 받는다: {@code @LoginUserId Long userId}.
 * 값은 요청 속성 {@link LoginUserIdArgumentResolver#REQUEST_ATTRIBUTE}에서 읽고, 없으면 {@code 401 UNAUTHORIZED}다.
 * 인증 필터가 토큰을 확인한 뒤 이 속성을 채운다 (local·test 프로필에서는 {@link DevLoginUserFilter}가 개발용 헤더로 채운다).
 * 로그인하지 않아도 부를 수 있는 API는 {@code @LoginUserId(required = false) Long userId}로 받는다. 토큰을 보내지 않았으면 null이고,
 * 보낸 토큰은 똑같이 확인한다 (만료 {@code 401 TOKEN_EXPIRED}, 틀림 {@code 401 UNAUTHORIZED}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUserId {

	/** false면 토큰 없이 부른 요청에 null을 넣는다 */
	boolean required() default true;
}
