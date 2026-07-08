package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

// 소비자 매칭 중지 API 응답에 필요한 취소 후 상태값 묶음
public record MatchingCancellationResult(
        Long matchingRequestId,
        MatchingStatus matchingStatus,
        MatchingRequestStatus requestStatus,
        MatchingRequestStatusReason requestStatusReason,
        Instant canceledAt
) {

    public static MatchingCancellationResult of(
            MatchingRequest matchingRequest,
            MatchingStatus matchingStatus
    ) {
        return new MatchingCancellationResult(
                matchingRequest.getId(),
                matchingStatus,
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                matchingRequest.getCanceledAt()
        );
    }
}
