package com.smu.healyx.hira.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * HIRA 진료과목 코드 Redis 캐싱 서비스.
 *
 * <p>캐싱 키: {@code hira:dgsbjt:{ykiho}}
 * TTL: 30일 (진료과목은 거의 불변)
 *
 * <p>현재 {@link HiraApiService#fetchDgsbjtCodes(String)}는 API 미신청으로 항상
 * {@code Optional.empty()}를 반환합니다. 따라서 이 서비스는 항상 빈 Set을 반환하며,
 * 호출 측({@link com.smu.healyx.agent.service.HospitalAgentService})은 빈 Set을 받으면
 * 이름 기반 fallback({@link com.smu.healyx.hira.util.DepartmentNameMatcher})으로 처리합니다.
 *
 * <p>API 신청 완료 후 {@code HiraApiService.fetchDgsbjtCodes} 본문을 활성화하면
 * 이 서비스의 캐싱·조회 로직이 자동 동작합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HiraDgsbjtCacheService {

    private final HiraApiService hiraApiService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration TTL = Duration.ofDays(30);
    private static final String KEY_PREFIX = "hira:dgsbjt:";

    private static final TypeReference<Set<String>> SET_TYPE = new TypeReference<>() {};

    /**
     * ykiho에 대한 진료과목 코드 Set을 반환합니다.
     *
     * <ol>
     *   <li>Redis 캐시 hit → 역직렬화 후 반환</li>
     *   <li>캐시 miss → {@code HiraApiService.fetchDgsbjtCodes} 호출</li>
     *   <li>결과 present → Redis SET (TTL 30일) 후 반환</li>
     *   <li>결과 empty → 빈 Set 반환 (negative 캐싱 없음)</li>
     * </ol>
     *
     * @param ykiho 암호화된 요양기호
     * @return 진료과목 코드 Set. API 미신청 또는 호출 실패 시 빈 Set.
     */
    public Set<String> getOrFetch(String ykiho) {
        String key = KEY_PREFIX + ykiho;

        // 1. Redis 캐시 hit 시도
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                Set<String> codes = objectMapper.readValue(cached, SET_TYPE);
                log.debug("hira:dgsbjt 캐시 hit: ykiho={}, codes={}", ykiho, codes);
                return codes;
            }
        } catch (Exception e) {
            log.warn("hira:dgsbjt 캐시 읽기 실패: ykiho={}, error={}", ykiho, e.getMessage());
            // Redis 장애 시 HIRA 직접 호출로 계속 진행
        }

        // 2. HIRA 직접 호출
        Optional<Set<String>> fetched = hiraApiService.fetchDgsbjtCodes(ykiho);

        if (fetched.isEmpty()) {
            // API 미신청 또는 호출 실패 — 캐시 저장 skip, 빈 Set 반환
            log.debug("hira:dgsbjt fetch 결과 없음 (API 미신청 또는 빈 응답): ykiho={}", ykiho);
            return Collections.emptySet();
        }

        Set<String> codes = fetched.get();

        // 3. Redis 캐싱 (TTL 30일)
        try {
            String json = objectMapper.writeValueAsString(codes);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("hira:dgsbjt 캐시 저장: ykiho={}, codes={}", ykiho, codes);
        } catch (Exception e) {
            log.warn("hira:dgsbjt 캐시 저장 실패: ykiho={}, error={}", ykiho, e.getMessage());
            // 캐시 저장 실패는 무시 — 다음 요청에서 재시도
        }

        return codes;
    }
}
