package org.sopt.ssingserver.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// SEARCHING으로 보이는 REQUESTED 요청의 주기적 재탐색 스케줄러
@Component
@RequiredArgsConstructor
public class MatchingSearchScheduler {

    private final MatchingSearchTriggerService matchingSearchTriggerService;

    // MVP 기준 1분 주기 REQUESTED 요청 스캔과 후보 생성/만료 여부 재판단
    @Scheduled(fixedDelay = 60_000)
    public void runScheduledSearch() {
        matchingSearchTriggerService.triggerAllRequested();
    }
}
