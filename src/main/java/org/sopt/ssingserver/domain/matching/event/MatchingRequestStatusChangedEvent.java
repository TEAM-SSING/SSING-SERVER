package org.sopt.ssingserver.domain.matching.event;

import java.time.Instant;
import java.util.UUID;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

// 취소/실패 등 요청 상태 변경 시 알림 계층 전달 이벤트
public record MatchingRequestStatusChangedEvent(
        // 알림 계층의 중복 발행 방어와 추적용 이벤트 id
        UUID eventId,
        // 상태 변경 판단 시각, 클라이언트 이벤트 정렬과 로그 분석 기준
        Instant occurredAt,
        // REST 상태 조회 복구의 기준 매칭 요청 id
        Long matchingRequestId,
        // DB에 저장된 요청 상태
        MatchingRequestStatus requestStatus,
        // DB에 저장된 실패/상태 전환 사유
        MatchingRequestStatusReason requestStatusReason,
        // 앱에 전달할 서버 계산 표시 상태
        MatchingStatus matchingStatus
) implements MatchingDomainEvent {
}
