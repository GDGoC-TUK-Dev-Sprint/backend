package com.tukorea.gdgoc.koready.auth;

import com.tukorea.gdgoc.koready.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JwtProvider jwtProvider, PasswordEncoder passwordEncoder) {
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    // 1. 회원가입 로직 (추후 DB 연결)
    public void signup(String loginId, String password, String name) {
        // TODO: 아이디 중복 확인
        
        // 비밀번호 안전하게 암호화
        String encodedPassword = passwordEncoder.encode(password);
        
        // TODO: DB에 사용자 정보 저장
    }

    // 2. 일반 로그인 로직 (추후 DB 연결)
    public String login(String loginId, String password) throws Exception {
        // TODO: DB에서 사용자 조회
        
        // TODO: passwordEncoder.matches(입력비번, DB비번) 로직으로 검증
        
        // 인증 성공 시 JWT 토큰 발급
        return jwtProvider.createAccessToken(loginId);
    }
}
