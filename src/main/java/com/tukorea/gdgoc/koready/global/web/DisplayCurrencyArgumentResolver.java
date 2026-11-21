package com.tukorea.gdgoc.koready.global.web;

import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.exception.FieldErrors;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link DisplayCurrency} 파라미터에 헤더 {@code X-Currency}로 고른 표시 통화를 넣는다 (API 명세 공통 규칙 "통화").
 * 없으면 KRW, 지원하지 않는 값이면 {@code 400 INVALID_REQUEST} (fields: X-Currency).
 * 언어({@code Accept-Language})처럼 앱이 설정한 값을 모든 요청에 싣는다.
 */
public class DisplayCurrencyArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == DisplayCurrency.class;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		try {
			return DisplayCurrency.fromHeader(webRequest.getHeader(DisplayCurrency.HEADER));
		} catch (IllegalArgumentException e) {
			new FieldErrors().add(DisplayCurrency.HEADER, "KRW·JPY·USD·EUR 중 하나여야 합니다").throwIfAny();
			return null; // throwIfAny가 항상 던진다
		}
	}
}
