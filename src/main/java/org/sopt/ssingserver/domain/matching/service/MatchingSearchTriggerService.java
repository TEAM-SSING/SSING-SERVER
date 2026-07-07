package org.sopt.ssingserver.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 요청 생성 직후 트리거와 1분 스케줄러의 공통 재탐색 입구
@Service
@RequiredArgsConstructor
public class MatchingSearchTriggerService {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingSearchService matchingSearchService;

    // 생성 커밋 이후 후속 DB 쓰기를 위한 새 트랜잭션 단건 재탐색
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void triggerSearch(Long matchingRequestId) {
        matchingSearchService.search(matchingRequestId);
    }

    // 스케줄러의 REQUESTED 요청 id 수집과 단건 탐색 트랜잭션별 상태 변경 위임
    public void triggerAllRequested() {
        matchingRequestRepository.findIdsByStatusOrderByIdAsc(MatchingRequestStatus.REQUESTED)
                .forEach(matchingSearchService::search);
    }
}
