package com.smu.healyx.hira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * HIRA Open API (getHospBasisList1) 병원 단건 응답 항목
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HiraItem {
    
    /** 암호화된 요양기호 — 병원 식별 핵심 키 */
    private String ykiho;

    /** 병원명 */
    private String yadmNm;

    /** 주소 */
    private String addr;

    /** 전화번호 */
    private String telno;

    /** 경도 (XPos) — 소수점 15자리 정밀도 */
    @JsonProperty("XPos")
    private double longitude;

    /** 위도 (YPos) — 소수점 15자리 정밀도 */
    @JsonProperty("YPos")
    private double latitude;

    /** 검색 위치 기준 거리 — HIRA API가 소수점 문자열로 반환하므로 double로 파싱 */
    @JsonProperty("distance")
    private double distance;

    /** 종별코드 */
    private String clCd;

    /** 종별코드명 */
    private String clCdNm;
}