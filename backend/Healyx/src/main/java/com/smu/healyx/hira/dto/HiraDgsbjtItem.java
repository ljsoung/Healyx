package com.smu.healyx.hira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * HIRA Open API (getDgsbjtInfo 계열) 진료과목 단건 응답 항목.
 *
 * <p>현재 HIRA 진료과목 정보 API 미신청 상태. API 신청 완료 후
 * {@link com.smu.healyx.hira.service.HiraApiService#fetchDgsbjtCodes(String)} 본문을 활성화하면
 * 이 DTO가 실제 응답 역직렬화에 사용됩니다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HiraDgsbjtItem {

    /** 진료과목코드 (예: "11"=안과, "01"=내과) */
    private String dgsbjtCd;

    /** 진료과목명 (예: "안과", "내과") */
    private String dgsbjtCdNm;
}
