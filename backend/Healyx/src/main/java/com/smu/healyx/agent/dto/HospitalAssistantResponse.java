package com.smu.healyx.agent.dto;

import com.smu.healyx.hira.dto.HospitalSearchResponse;
import lombok.Builder;
import lombok.Getter;

/** Agent → Flutter 통합 응답 DTO */
@Getter
@Builder
public class HospitalAssistantResponse {

    /** HIRA 진료과목 코드 (예: "12") */
    private String departmentCode;

    /** 진료과 이름 (예: "이비인후과") */
    private String departmentName;

    /** 병원 목록 (HIRA API 결과) */
    private HospitalSearchResponse hospitals;

    /** ICD-10 코드 */
    private String icd10Code;
}
