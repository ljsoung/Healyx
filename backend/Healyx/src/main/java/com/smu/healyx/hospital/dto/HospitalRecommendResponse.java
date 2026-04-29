package com.smu.healyx.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class HospitalRecommendResponse {
    private int riskLevel;
    private String department;
    private double searchRadiusKm;
    private List<HospitalSummaryDto> hospitals;
}