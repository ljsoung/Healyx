package com.smu.healyx.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class VerifyCurrentPasswordRequest {

    // 1단계: 현재 비밀번호 검증
    @NotBlank
    private String currentPassword;
}
