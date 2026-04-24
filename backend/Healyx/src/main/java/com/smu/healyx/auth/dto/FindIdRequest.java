package com.smu.healyx.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class FindIdRequest {

    // 실명
    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    // 이메일 인증번호
    @NotBlank
    private String verificationCode;
}
