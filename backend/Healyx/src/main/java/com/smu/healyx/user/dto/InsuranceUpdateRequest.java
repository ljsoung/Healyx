package com.smu.healyx.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InsuranceUpdateRequest {

    // 허용값: "insured" (가입) / "uninsured" (미가입)
    @NotNull(message = "insuranceStatus는 필수입니다.")
    @Pattern(
        regexp = "^(insured|uninsured)$",
        message = "유효하지 않은 값입니다. 허용값: insured, uninsured"
    )
    private String insuranceStatus;
}
