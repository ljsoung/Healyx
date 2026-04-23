package com.smu.healyx.agent.controller;

import com.smu.healyx.agent.dto.HospitalAssistantRequest;
import com.smu.healyx.agent.dto.HospitalAssistantResponse;
import com.smu.healyx.agent.service.HospitalAgentService;
import com.smu.healyx.common.dto.ApiResponse;
import com.smu.healyx.common.security.SecurityUtils;
import com.smu.healyx.user.dto.UserProfileDto;
import com.smu.healyx.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Agent 통합 엔드포인트 — 게스트·로그인 사용자 모두 허용.
 *
 * POST /api/agent/hospital-assistant
 *
 * 로그인 사용자: JWT → userId → DB에서 나이·성별·보험 조회 → 개인화 의료비 예측
 * 게스트:        기본값(나이=0, 성별=null, 보험=false) → ICD-10 코드만으로 의료비 계산
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class HospitalAgentController {

    private final HospitalAgentService hospitalAgentService;
    private final UserProfileService userProfileService;

    @PostMapping("/hospital-assistant")
    public ResponseEntity<ApiResponse<HospitalAssistantResponse>> hospitalAssistant(
            @Valid @RequestBody HospitalAssistantRequest request,
            Authentication authentication) {

        UserProfileDto userProfile = resolveUserProfile(authentication);
        HospitalAssistantResponse response = hospitalAgentService.run(request, userProfile);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 인증 여부에 따라 사용자 프로필을 결정합니다.
     * - 로그인: JWT에서 userId 추출 → DB 조회
     * - 게스트: 기본값 반환 (의료비 보정 미적용)
     */
    private UserProfileDto resolveUserProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return UserProfileDto.guestDefault();
        }
        Long userId = SecurityUtils.extractUserId(authentication);
        return userProfileService.getProfile(userId);
    }
}
