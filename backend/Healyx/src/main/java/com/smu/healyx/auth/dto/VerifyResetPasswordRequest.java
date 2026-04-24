package com.smu.healyx.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class VerifyResetPasswordRequest {

    // 1단계: 아이디 + 이메일 + 인증번호 검증
    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String verificationCode;
}
