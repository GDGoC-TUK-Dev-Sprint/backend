package com.tukorea.gdgoc.koready.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SignupRequest {

    @NotBlank(message = "아이디는 필수 입력값입니다.")
    @Size(min = 4, max = 30, message = "아이디는 4자 이상, 30자 이하로 입력해주세요.")
    @Pattern(regexp = "^[a-z0-9]+$", message = "아이디는 영문 소문자와 숫자만 사용할 수 있습니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상, 64자 이하로 입력해주세요.")
    private String password;

    @NotBlank(message = "이름은 필수 입력값입니다.")
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
