package org.sopt.ssingserver.domain.matching.dto.result;

import java.time.Instant;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

// 매칭 요청 생성 Service의 Controller 전달용 내부 결과, 생성 직후 REST 응답 계약 원천 값
public record MatchingCreationResult(
        Long matchingRequestId,
        MatchingStatus matchingStatus,
        MatchingRequestStatus requestStatus,
        MatchingRequestStatusReason requestStatusReason,
        Long groupId,
        MatchingRequestGroupStatus groupStatus,
        Instant expiresAt
) {

    // 생성 직후 후보 탐색 결과와 무관한 SEARCHING/REQUESTED 최초 응답
    public static MatchingCreationResult searching(MatchingRequest matchingRequest) {
        return new MatchingCreationResult(
                matchingRequest.getId(),
                MatchingStatus.SEARCHING,
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                null,
                null,
                matchingRequest.getExpiresAt()
        );
    }
}
