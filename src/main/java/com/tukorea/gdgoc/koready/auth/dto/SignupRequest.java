package com.tukorea.gdgoc.koready.auth.dto;

public class SignupRequest {

    private String loginId;
    private String password;
    private String name;

    // 기본 생성자
    public SignupRequest() {}

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
