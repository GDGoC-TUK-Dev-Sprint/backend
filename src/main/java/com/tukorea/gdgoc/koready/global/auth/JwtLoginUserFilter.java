package com.tukorea.gdgoc.koready.global.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 헤더 {@code Authorization: Bearer <액세스 토큰>}이 유효하면 로그인한 사용자 ID를 요청 속성에 넣는다.
 * 이후 {@link LoginUserId} 파라미터에 그 값이 전달된다. 토큰이 없거나 틀리면 아무것도 하지 않고,
 * 로그인이 필요한 API에서 LoginUserIdArgumentResolver가 UNAUTHORIZED로 막는다.
 */
@Component
class JwtLoginUserFilter extends OncePerRequestFilter {

	private final JwtProvider jwtProvider;

	JwtLoginUserFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			jwtProvider.parseAccessToken(header.substring(7).trim())
					.ifPresent(userId -> request.setAttribute(LoginUserIdArgumentResolver.REQUEST_ATTRIBUTE, userId));
		}
		chain.doFilter(request, response);
	}
}
