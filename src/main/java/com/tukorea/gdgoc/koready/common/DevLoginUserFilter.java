package com.tukorea.gdgoc.koready.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인이 생기기 전 개발용: 헤더 {@code X-Dev-User-Id}의 사용자 ID를 로그인한 사용자로 본다.
 * local·test 프로필에서만 켜진다 (배포 서버에서는 쓰지 않는다).
 */
@Component
@Profile({"local", "test"})
class DevLoginUserFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Dev-User-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String value = request.getHeader(HEADER);
		if (value != null && value.matches("[1-9][0-9]{0,17}")) {
			request.setAttribute(LoginUserIdArgumentResolver.REQUEST_ATTRIBUTE, Long.valueOf(value));
		}
		chain.doFilter(request, response);
	}
}
