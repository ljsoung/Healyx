package com.smu.healyx.hospital.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HospitalRecommendRequest {

    @NotBlank(message = "증상을 입력해주세요. 음성 인식에 실패한 경우 텍스트로 직접 입력해주세요.")
    private String symptom;

    @NotNull(message = "위치 정보(위도)를 확인할 수 없습니다. GPS 권한을 확인해주세요.")
    private Double latitude;

    @NotNull(message = "위치 정보(경도)를 확인할 수 없습니다. GPS 권한을 확인해주세요.")
    private Double longitude;

    @Pattern(
            regexp = "^(en|zh|vi|th|ja)$",
            message = "지원하지 않는 언어 코드입니다. 지원 언어: en, zh, vi, th, ja"
    )
    private String languageCode = "en";

    @Min(value = 1, message = "riskLevel 범위 오류: 슬라이더 범위(1~5)를 벗어난 값이 전달되었습니다.")
    @Max(value = 5, message = "riskLevel 범위 오류: 슬라이더 범위(1~5)를 벗어난 값이 전달되었습니다.")
    private Integer riskLevel;

    private String sortBy = "score";

    public String getEffectiveLanguageCode() {
        return (languageCode == null || languageCode.isBlank()) ? "en" : languageCode;
    }

    public boolean isDistanceSort() {
        return "distance".equalsIgnoreCase(sortBy);
    }
}