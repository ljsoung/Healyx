package com.smu.healyx.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LanguageUpdateRequest {

    // 지원 언어: ko, zh, vi, th, en, ja
    @NotBlank(message = "언어 코드는 필수입니다.")
    @Pattern(
        regexp = "^(ko|zh|vi|th|en|ja)$",
        message = "지원하지 않는 언어 코드입니다. 지원 언어: ko, zh, vi, th, en, ja"
    )
    private String languageCode;
}
