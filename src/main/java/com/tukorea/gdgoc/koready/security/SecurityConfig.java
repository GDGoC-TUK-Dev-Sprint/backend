package com.tukorea.gdgoc.koready.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // REST API이므로 CSRF 비활성화
            .csrf(AbstractHttpConfigurer::disable)
            // JWT를 사용하므로 세션을 Stateless로 설정
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 엔드포인트 접근 권한: Security는 모든 요청을 통과시키고, 로그인 확인은 @LoginUserId 리졸버가 한다
            // (JwtLoginUserFilter가 넣은 사용자 ID가 없으면 401 UNAUTHORIZED). 로그인이 필요 없는 API를 여기에 따로 적지 않는다
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            
        return http.build();
    }
}

   