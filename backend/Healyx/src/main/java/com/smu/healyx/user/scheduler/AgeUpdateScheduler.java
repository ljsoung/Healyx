package com.smu.healyx.user.scheduler;

import com.smu.healyx.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgeUpdateScheduler {

    private final UserRepository userRepository;

    /**
     * 매년 1월 1일 자정에 전체 사용자 만 나이를 일괄 갱신합니다.
     * MySQL TIMESTAMPDIFF(YEAR, birth_date, CURDATE())로 정확한 만 나이를 계산합니다.
     */
    @Scheduled(cron = "0 0 0 1 1 *")
    @Transactional
    public void updateAllAges() {
        log.info("나이 일괄 갱신 배치 시작");
        int updatedCount = userRepository.updateAllAges();
        log.info("나이 일괄 갱신 배치 완료: {}명 갱신", updatedCount);
    }
}
