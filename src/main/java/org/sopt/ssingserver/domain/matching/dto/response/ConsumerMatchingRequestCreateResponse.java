package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCreationResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsumerMatchingRequestCreateResponse(
        @Schema(description = "매칭 요청 ID", example = "1")
        Long matchingRequestId,

        @Schema(description = "Android 화면 전환용 매칭 상태", example = "SEARCHING")
        MatchingStatus matchingStatus,

        @Schema(description = "매칭 요청 DB 상태", example = "REQUESTED")
        MatchingRequestStatus requestStatus,

        @Schema(description = "매칭 요청 상태 변경 사유", example = "NO_AVAILABLE_INSTRUCTOR")
        MatchingRequestStatusReason requestStatusReason,

        @Schema(description = "매칭 요청 그룹 ID", example = "1")
        Long groupId,

        @Schema(description = "매칭 요청 그룹 상태", example = "PROPOSED")
        MatchingRequestGroupStatus groupStatus,

        @Schema(description = "매칭 요청 탐색 만료 시각", example = "2026-06-28T06:35:00Z")
        Instant expiresAt
) {

    public static ConsumerMatchingRequestCreateResponse from(MatchingCreationResult result) {
        return new ConsumerMatchingRequestCreateResponse(
                result.matchingRequestId(),
                result.matchingStatus(),
                result.requestStatus(),
                result.requestStatusReason(),
                result.groupId(),
                result.groupStatus(),
                result.expiresAt()
        );
    }
}
