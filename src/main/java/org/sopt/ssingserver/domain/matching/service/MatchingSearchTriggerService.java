package org.sopt.ssingserver.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.springframework.stereotype.Service;

// 요청 생성 직후 트리거와 1분 스케줄러의 공통 재탐색 입구
@Service
@RequiredArgsConstructor
public class MatchingSearchTriggerService {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingSearchService matchingSearchService;

    // 생성 직후 즉시 트리거용 특정 요청 단건 재탐색
    public void triggerSearch(Long matchingRequestId) {
        matchingSearchService.search(matchingRequestId);
    }

    // 스케줄러의 REQUESTED 요청 id 수집과 단건 탐색 트랜잭션별 상태 변경 위임
    public void triggerAllRequested() {
        matchingRequestRepository.findIdsByStatusOrderByIdAsc(MatchingRequestStatus.REQUESTED)
                .forEach(matchingSearchService::search);
    }
}
