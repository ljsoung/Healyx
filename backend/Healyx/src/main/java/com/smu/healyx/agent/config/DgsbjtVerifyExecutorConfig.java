package com.smu.healyx.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 진료과목 검증 병렬 처리용 Executor 빈 설정.
 *
 * <p>HIRA {@code getDgsbjtInfo} 호출을 병원 수만큼 동시 실행하기 위해
 * 별도 스레드풀을 사용합니다. 동시 호출 수 상한 10으로 HIRA 일일 10,000 제한 대비 안전.
 *
 * <ul>
 *   <li>코어 풀 크기: 10</li>
 *   <li>최대 풀 크기: 10</li>
 *   <li>큐 용량: 50</li>
 *   <li>유지 시간: 60초</li>
 * </ul>
 */
@Configuration
public class DgsbjtVerifyExecutorConfig {

    /**
     * 진료과목 검증 병렬 Executor.
     * {@link com.smu.healyx.agent.service.HospitalAgentService}에서
     * {@code CompletableFuture.supplyAsync} 두 번째 인자로 주입됩니다.
     */
    @Bean(name = "dgsbjtVerifyExecutor")
    public Executor dgsbjtVerifyExecutor() {
        return new ThreadPoolExecutor(
                10,                           // 코어 풀 크기
                10,                           // 최대 풀 크기
                60L, TimeUnit.SECONDS,        // 유휴 스레드 유지 시간
                new LinkedBlockingQueue<>(50) // 큐 용량
        );
    }
}
