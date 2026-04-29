package com.smu.healyx.hospital.controller;

import com.smu.healyx.common.dto.ApiResponse;
import com.smu.healyx.hospital.dto.HospitalRecommendRequest;
import com.smu.healyx.hospital.dto.HospitalRecommendResponse;
import com.smu.healyx.hospital.service.HospitalRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Hospital", description = "병원 찾기 API")
@RestController
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
public class HospitalRecommendController {

    private final HospitalRecommendService hospitalRecommendService;

    @Operation(summary = "병원 추천", description = "증상 분석 후 위치 기반 병원 추천 (Top 5)")
    @PostMapping("/recommend")
    public ResponseEntity<ApiResponse<HospitalRecommendResponse>> recommend(
            @Valid @RequestBody HospitalRecommendRequest request) {
        return ResponseEntity.ok(ApiResponse.success(hospitalRecommendService.recommend(request)));
    }
}