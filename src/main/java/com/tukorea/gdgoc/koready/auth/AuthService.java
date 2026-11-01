package com.tukorea.gdgoc.koready.auth;

import com.tukorea.gdgoc.koready.domain.user.entity.User;
import com.tukorea.gdgoc.koready.domain.user.repository.UserRepository;
import com.tukorea.gdgoc.koready.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public AuthService(JwtProvider jwtProvider, PasswordEncoder passwordEncoder, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    // 1. 회원가입 로직
    public void signup(String loginId, String password, String name) {
        // 아이디 중복 확인
        if (userRepository.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        
        // 비밀번호 안전하게 암호화
        String encodedPassword = passwordEncoder.encode(password);
        
        // DB에 사용자 정보 저장
        User newUser = new User(loginId, encodedPassword, name);
        userRepository.save(newUser);
    }

    // 2. 일반 로그인 로직
    public String login(String loginId, String password) throws Exception {
        // DB에서 사용자 조회
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 아이디입니다."));
        
        // 비밀번호 검증
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        
        // 인증 성공 시 JWT 토큰 발급
        return jwtProvider.createAccessToken(user.getLoginId());
    }
}
