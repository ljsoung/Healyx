package com.smu.healyx.common.security;

import org.springframework.security.core.Authentication;

/**
 * JWT 인증 유틸리티.
 *
 * JWT 필터 구현 완료 후:
 *   JWT claim의 userId(Long)를 Authentication principal로 설정하면
 *   extractUserId()가 그대로 동작합니다.
 *
 * 예시 (JWT 필터 내):
 *   Long userId = jwtProvider.getUserId(token);
 *   UsernamePasswordAuthenticationToken auth =
 *       new UsernamePasswordAuthenticationToken(userId, null, authorities);
 *   SecurityContextHolder.getContext().setAuthentication(auth);
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }

        // JWT 필터가 구현되면 이 경로에 도달하지 않습니다.
        throw new IllegalStateException("JWT 필터 미구현: 인증 정보에서 userId를 추출할 수 없습니다.");
    }
}
