package com.tukorea.gdgoc.koready.auth;

import com.tukorea.gdgoc.koready.auth.dto.LoginRequest;
import com.tukorea.gdgoc.koready.auth.dto.SignupRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<String> signup(@RequestBody SignupRequest request) {
        // 서비스의 회원가입 로직 호출
        authService.signup(request.getLoginId(), request.getPassword(), request.getName());
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) throws Exception {
        // 서비스의 로그인 로직 호출 후 토큰 반환
        String token = authService.login(request.getLoginId(), request.getPassword());
        return ResponseEntity.ok(token); // 실제로는 JSON 형태로 반환하는 것이 좋습니다.
    }
}
