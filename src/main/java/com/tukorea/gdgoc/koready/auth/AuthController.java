package com.tukorea.gdgoc.koready.auth;

import com.tukorea.gdgoc.koready.auth.dto.LoginRequest;
import com.tukorea.gdgoc.koready.auth.dto.SignupRequest;
import jakarta.validation.Valid;
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
    // @Valid를 붙여야 SignupRequest에 작성한 글자 수 제한 등이 실제로 검사됩니다.
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request.getLoginId(), request.getPassword(), request.getName());
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) throws Exception {
        String token = authService.login(request.getLoginId(), request.getPassword());
        return ResponseEntity.ok(token);
    }
}
