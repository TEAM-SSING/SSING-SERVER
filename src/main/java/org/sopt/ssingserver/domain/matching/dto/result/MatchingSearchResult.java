package org.sopt.ssingserver.domain.matching.dto.result;

import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

// 즉시 트리거 또는 스케줄러 탐색 결과와 현재 표시 상태 전달
public record MatchingSearchResult(
        Long matchingRequestId,
        MatchingStatus matchingStatus,
        MatchingRequestStatus requestStatus,
        MatchingRequestStatusReason requestStatusReason,
        Long groupId,
        MatchingRequestGroupStatus groupStatus
) {

    // 그룹/제안이 없는 탐색 결과의 요청 상태와 표시 상태만 포함
    public static MatchingSearchResult of(
            MatchingRequest matchingRequest,
            MatchingStatus matchingStatus
    ) {
        return new MatchingSearchResult(
                matchingRequest.getId(),
                matchingStatus,
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                null,
                null
        );
    }

    // 이미 처리되었거나 REQUESTED가 아닌 요청 재호출 시 새 작업 없는 멱등 결과 반환
    public static MatchingSearchResult searching(Long matchingRequestId) {
        return new MatchingSearchResult(
                matchingRequestId,
                MatchingStatus.SEARCHING,
                null,
                null,
                null,
                null
        );
    }

    // 그룹 생성 탐색 결과의 앱 표시 상태와 그룹 id/status 전달
    public static MatchingSearchResult of(
            MatchingRequest matchingRequest,
            MatchingStatus matchingStatus,
            MatchingRequestGroup matchingRequestGroup
    ) {
        return new MatchingSearchResult(
                matchingRequest.getId(),
                matchingStatus,
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                matchingRequestGroup.getId(),
                matchingRequestGroup.getStatus()
        );
    }
}
