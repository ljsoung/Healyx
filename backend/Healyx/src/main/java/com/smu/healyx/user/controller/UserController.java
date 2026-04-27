package com.smu.healyx.user.controller;

import com.smu.healyx.common.dto.ApiResponse;
import com.smu.healyx.common.security.SecurityUtils;
import com.smu.healyx.user.dto.InsuranceUpdateRequest;
import com.smu.healyx.user.dto.LanguageUpdateRequest;
import com.smu.healyx.user.dto.MyProfileResponse;
import com.smu.healyx.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 프로필 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    /** 내 프로필 조회 — 자동 로그인 및 프로필 화면에서 호출 */
    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(
            Authentication authentication) {
        Long userId = SecurityUtils.extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getMyProfile(userId)));
    }

    /** 선호 언어 변경 — 로그인 사용자 전용, 게스트는 SharedPreferences에만 저장 */
    @Operation(summary = "선호 언어 변경", description = "지원 언어: ko, zh, vi, th, en, ja")
    @PatchMapping("/me/language")
    public ResponseEntity<ApiResponse<Void>> updateLanguage(
            @Valid @RequestBody LanguageUpdateRequest request,
            Authentication authentication) {
        Long userId = SecurityUtils.extractUserId(authentication);
        userProfileService.updateLanguage(userId, request.getLanguageCode());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 건강보험 가입 상태 변경 — 저장 버튼 클릭 시 호출 */
    @Operation(summary = "건강보험 가입 상태 변경", description = "insuranceStatus: insured(가입) / uninsured(미가입)")
    @PatchMapping("/me/insurance")
    public ResponseEntity<ApiResponse<Void>> updateInsuranceStatus(
            @Valid @RequestBody InsuranceUpdateRequest request,
            Authentication authentication) {
        Long userId = SecurityUtils.extractUserId(authentication);
        userProfileService.updateInsuranceStatus(userId, request.getInsuranceStatus());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
