package com.smu.healyx.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HospitalRecommendRequest {

    @NotBlank(message = "증상을 입력해주세요.")
    private String symptom;

    @NotNull(message = "위도를 입력해주세요.")
    private Double latitude;

    @NotNull(message = "경도를 입력해주세요.")
    private Double longitude;

    private String languageCode = "en";

    public String getEffectiveLanguageCode() {
        return (languageCode == null || languageCode.isBlank()) ? "en" : languageCode;
    }
}