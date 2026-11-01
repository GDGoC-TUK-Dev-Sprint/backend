package com.tukorea.gdgoc.koready.auth.dto;

public class LoginRequest {

    private String loginId;
    private String password;

    // 기본 생성자
    public LoginRequest() {}

    // Getter 및 Setter
    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
