package com.smu.healyx.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SymptomAnalysisResult {
    private int riskLevel;
    private String department;
}