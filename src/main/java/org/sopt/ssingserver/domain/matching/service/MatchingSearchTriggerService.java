package org.sopt.ssingserver.domain.matching.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

// 요청 생성 직후 트리거와 주기 스케줄러의 공통 재탐색 입구
@Service
@RequiredArgsConstructor
public class MatchingSearchTriggerService {

    private static final Logger log = LoggerFactory.getLogger(MatchingSearchTriggerService.class);
    private static final int REQUESTED_SCAN_BATCH_SIZE = 100;

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingSearchService matchingSearchService;

    // 단건 탐색 위임, 후속 DB 쓰기의 새 트랜잭션은 MatchingSearchService가 요청별로 관리
    public void triggerSearch(Long matchingRequestId) {
        matchingSearchService.search(matchingRequestId);
    }

    // 스케줄러의 REQUESTED 요청 id 배치 수집과 단건 탐색 트랜잭션별 상태 변경 위임
    public void triggerAllRequested() {
        List<Long> matchingRequestIds = matchingRequestRepository.findIdsByStatusOrderByIdAsc(
                MatchingRequestStatus.REQUESTED,
                PageRequest.of(0, REQUESTED_SCAN_BATCH_SIZE)
        );

        int successCount = 0;
        int failureCount = 0;
        for (Long matchingRequestId : matchingRequestIds) {
            try {
                matchingSearchService.search(matchingRequestId);
                successCount++;
            } catch (RuntimeException exception) {
                failureCount++;
                logSearchFailure(matchingRequestId, exception);
            }
        }

        logSearchSummary(matchingRequestIds.size(), successCount, failureCount);
    }

    // 단건 재탐색 실패의 다음 요청 전파 차단과 원인 추적용 구조화 로그 기록
    private void logSearchFailure(Long matchingRequestId, RuntimeException exception) {
        log.atWarn()
                .addKeyValue("event", "matching.search.request.failed")
                .addKeyValue("matching_request_id", String.valueOf(matchingRequestId))
                .setCause(exception)
                .log("Matching search request failed");
    }

    // 비어 있지 않은 스케줄러 배치의 처리 결과 집계 로그 기록
    private void logSearchSummary(int requestedCount, int successCount, int failureCount) {
        if (requestedCount == 0) {
            return;
        }

        log.atInfo()
                .addKeyValue("event", "matching.search.batch.completed")
                .addKeyValue("requested_count", requestedCount)
                .addKeyValue("success_count", successCount)
                .addKeyValue("failure_count", failureCount)
                .log("Matching search batch completed");
    }
}
