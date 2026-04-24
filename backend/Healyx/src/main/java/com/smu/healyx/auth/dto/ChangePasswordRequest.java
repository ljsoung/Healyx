package com.smu.healyx.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class ChangePasswordRequest {

    // 2단계: 새 비밀번호 변경 (현재 비밀번호 검증은 1단계에서 완료)
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{7,12}$",
             message = "비밀번호는 영문, 숫자, 특수기호를 포함한 7~12자여야 합니다.")
    private String newPassword;

    // 새 비밀번호 확인 (newPassword와 일치해야 함)
    @NotBlank
    private String confirmNewPassword;
}
