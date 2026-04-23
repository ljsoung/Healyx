package com.smu.healyx.user.dto;

import lombok.Builder;
import lombok.Getter;

/** AI Agent·의료비 예측에 필요한 사용자 프로필 (DB 조회 결과 또는 게스트 기본값) */
@Getter
@Builder
public class UserProfileDto {

    /** 나이 (게스트: 0) */
    private int age;

    /** 성별 (게스트: null) */
    private String gender;

    /** 건강보험 가입 여부 (게스트: false) */
    private boolean insured;

    /**
     * 게스트 기본값.
     * 로그인하지 않은 경우 의료비 예측은 ICD-10 코드만으로 계산하며
     * 나이·성별·보험 보정 계수는 적용하지 않습니다.
     */
    public static UserProfileDto guestDefault() {
        return UserProfileDto.builder()
                .age(0)
                .gender(null)
                .insured(false)
                .build();
    }

    /** 게스트 여부 (나이·성별 미제공 상태) */
    public boolean isGuest() {
        return age == 0 && gender == null;
    }
}
