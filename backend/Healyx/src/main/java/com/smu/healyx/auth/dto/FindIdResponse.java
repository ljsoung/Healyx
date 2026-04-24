package com.smu.healyx.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FindIdResponse {
    // 앞 4자리만 표시, 나머지는 * 마스킹 (예: kimc***)
    private String maskedUsername;
}
