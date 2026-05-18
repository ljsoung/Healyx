package com.smu.healyx.hira.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * HaversineUtils 단위 테스트 (TC-7)
 *
 * Haversine 공식 거리 계산 정확성 검증.
 * Spring 컨텍스트 없이 순수 Java 테스트로 실행 가능.
 */
@DisplayName("HaversineUtils 단위 테스트 (TC-7)")
class HaversineUtilsTest {

    @Test
    @DisplayName("TC-7-1: 동일 좌표 간 거리 → 0m")
    void distanceMeters_sameCoords_returnsZero() {
        double dist = HaversineUtils.distanceMeters(37.5665, 126.9780, 37.5665, 126.9780);
        assertThat(dist)
                .as("동일 좌표 간 거리는 0m 이어야 함")
                .isEqualTo(0.0, within(0.001));
    }

    @Test
    @DisplayName("TC-7-2: 서울~부산 거리 → 300000m ~ 350000m 범위")
    void distanceMeters_seoulToBusan_isBetween300And350km() {
        // 서울: 37.5665, 126.9780 / 부산: 35.1796, 129.0756
        double dist = HaversineUtils.distanceMeters(37.5665, 126.9780, 35.1796, 129.0756);
        assertThat(dist)
                .as("서울~부산 거리는 약 325km (300000~350000m 범위)")
                .isBetween(300_000.0, 350_000.0);
    }
}
