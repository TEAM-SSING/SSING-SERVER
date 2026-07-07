package org.sopt.ssingserver.domain.matching.event;

import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

// 탐색 만료 실패 등 요청 상태 변경 시 알림 계층 전달 이벤트
public record MatchingRequestStatusChangedEvent(
        Long matchingRequestId,
        MatchingRequestStatus requestStatus,
        MatchingRequestStatusReason requestStatusReason,
        MatchingStatus matchingStatus
) implements MatchingDomainEvent {
}
