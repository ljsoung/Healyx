package com.smu.healyx.hira.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SidoCodeResolver 단위 테스트 (TC-6)
 *
 * GPS 좌표 → HIRA sidoCd 변환 정확성 검증.
 * Spring 컨텍스트 없이 순수 Java 테스트로 실행 가능.
 */
@DisplayName("SidoCodeResolver 단위 테스트 (TC-6)")
class SidoCodeResolverTest {

    @Test
    @DisplayName("TC-6-1: 서울 좌표(37.5665, 126.9780) → 110000")
    void resolve_seoulCoords_returns110000() {
        String result = SidoCodeResolver.resolve(37.5665, 126.9780);
        assertThat(result)
                .as("서울 중심 좌표는 sidoCd=110000(서울특별시) 이어야 함")
                .isEqualTo("110000");
    }

    @Test
    @DisplayName("TC-6-2: 부산 좌표(35.1796, 129.0756) → 210000")
    void resolve_busanCoords_returns210000() {
        String result = SidoCodeResolver.resolve(35.1796, 129.0756);
        assertThat(result)
                .as("부산 중심 좌표는 sidoCd=210000(부산광역시) 이어야 함")
                .isEqualTo("210000");
    }

    @Test
    @DisplayName("TC-6-3: 제주 좌표(33.4996, 126.5312) → 390000")
    void resolve_jejuCoords_returns390000() {
        String result = SidoCodeResolver.resolve(33.4996, 126.5312);
        assertThat(result)
                .as("제주 중심 좌표는 sidoCd=390000(제주특별자치도) 이어야 함")
                .isEqualTo("390000");
    }

    @Test
    @DisplayName("TC-6-4: bbox 외 좌표(0.0, 0.0) → null (전국 fallback)")
    void resolve_outOfBboxCoords_returnsNull() {
        String result = SidoCodeResolver.resolve(0.0, 0.0);
        assertThat(result)
                .as("한국 bbox 외 좌표는 null을 반환해야 함 (전국 조회 fallback)")
                .isNull();
    }
}
