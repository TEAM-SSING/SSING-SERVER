package org.sopt.ssingserver.domain.matching.dto.result;

import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

// 즉시 트리거 또는 스케줄러 탐색 결과와 현재 표시 상태 전달
public record MatchingSearchResult(
        // 재탐색 대상 매칭 요청 id, 상태 변경이 없어도 추적 가능한 기준값
        Long matchingRequestId,
        // 앱에 보여줄 계산 상태, skipped 결과에서는 새 상태 계산 없음 표현을 위해 null 허용
        MatchingStatus matchingStatus,
        // DB 저장 요청 상태, skipped 결과에서는 이미 다른 흐름이 처리한 상태라 null 허용
        MatchingRequestStatus requestStatus,
        // DB 저장 실패 사유, 최종 실패가 아닌 결과에서는 null 허용
        MatchingRequestStatusReason requestStatusReason,
        // 그룹이 생성된 탐색 결과의 그룹 id, 그룹 미생성 결과에서는 null
        Long groupId,
        // 그룹이 생성된 탐색 결과의 그룹 노출 상태, 그룹 미생성 결과에서는 null
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

    // 이미 처리되었거나 REQUESTED가 아닌 요청 재호출 시 상태 계산 없는 멱등 결과 반환
    // SEARCHING 재계산 방지를 통한 중복 트리거의 오해 소지 없는 no-op 표현
    public static MatchingSearchResult skipped(Long matchingRequestId) {
        return new MatchingSearchResult(
                matchingRequestId,
                null,
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
