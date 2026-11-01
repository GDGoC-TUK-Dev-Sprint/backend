package com.tukorea.gdgoc.koready.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup() {
        // TODO: 요청 데이터를 받을 DTO 생성 및 연결
        return ResponseEntity.ok("회원가입 API 호출 성공");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login() {
        // TODO: 요청 데이터를 받을 DTO 생성 및 연결
        return ResponseEntity.ok("로그인 API 호출 성공");
    }
}
