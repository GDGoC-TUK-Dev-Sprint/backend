package com.tukorea.gdgoc.koready.global.auth;
import com.tukorea.gdgoc.koready.security.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 헤더 {@code Authorization: Bearer <액세스 토큰>}이 유효하면 로그인한 사용자 ID를 요청 속성에 넣는다.
 * 이후 {@link LoginUserId} 파라미터에 그 값이 전달된다. 토큰이 만료됐으면 만료 표시만 남기고,
 * 로그인이 필요한 API에서 LoginUserIdArgumentResolver가 TOKEN_EXPIRED(만료)·UNAUTHORIZED(없음·틀림)로 막는다.
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
		String token = bearerToken(request.getHeader("Authorization"));
		if (token != null) {
			Optional<Long> userId = jwtProvider.parseAccessToken(token);
			if (userId.isPresent()) {
				request.setAttribute(LoginUserIdArgumentResolver.REQUEST_ATTRIBUTE, userId.get());
			} else if (jwtProvider.isExpiredAccessToken(token)) {
				request.setAttribute(LoginUserIdArgumentResolver.EXPIRED_ATTRIBUTE, Boolean.TRUE);
			}
		}
		chain.doFilter(request, response);
	}

	/** {@code Authorization} 헤더의 Bearer 토큰. 스킴은 대소문자를 가리지 않는다. Bearer 헤더가 아니면 null */
	static String bearerToken(String header) {
		return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).trim() : null;
	}
}
