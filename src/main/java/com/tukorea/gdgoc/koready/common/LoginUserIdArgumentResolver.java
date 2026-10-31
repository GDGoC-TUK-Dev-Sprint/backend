package com.tukorea.gdgoc.koready.common;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUserId} 파라미터에 요청 속성의 사용자 ID를 넣는다.
 */
public class LoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {

	/** 인증 필터가 로그인한 사용자 ID(Long)를 넣는 요청 속성 이름 */
	public static final String REQUEST_ATTRIBUTE = LoginUserId.class.getName();

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
		throw new ApiException(ErrorCode.UNAUTHORIZED);
	}
}
