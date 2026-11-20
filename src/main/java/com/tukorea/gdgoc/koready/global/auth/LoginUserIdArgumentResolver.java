package com.tukorea.gdgoc.koready.global.auth;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUserId} 파라미터에 요청 속성의 사용자 ID를 넣는다. 없으면 액세스 토큰이 만료됐을 때
 * {@code 401 TOKEN_EXPIRED}(앱이 재발급을 부른다), 그 밖에는 {@code 401 UNAUTHORIZED}다.
 */
public class LoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {

	/** 인증 필터가 로그인한 사용자 ID(Long)를 넣는 요청 속성 이름 */
	public static final String REQUEST_ATTRIBUTE = LoginUserId.class.getName();

	/** 인증 필터가 "서명은 맞지만 만료된 액세스 토큰"일 때 TRUE를 넣는 요청 속성 이름 */
	public static final String EXPIRED_ATTRIBUTE = LoginUserId.class.getName() + ".expired";

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginUserId.class)
				&& (parameter.getParameterType() == Long.class || parameter.getParameterType() == long.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		if (webRequest.getAttribute(REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof Long userId) {
			return userId;
		}
		if (Boolean.TRUE.equals(webRequest.getAttribute(EXPIRED_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST))) {
			throw new ApiException(ErrorCode.TOKEN_EXPIRED);
		}
		throw new ApiException(ErrorCode.UNAUTHORIZED);
	}
}
