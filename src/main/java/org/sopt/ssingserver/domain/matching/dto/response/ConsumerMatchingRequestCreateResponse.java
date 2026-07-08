package org.sopt.ssingserver.domain.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCreationResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

public record ConsumerMatchingRequestCreateResponse(
        @Schema(description = "매칭 요청 ID", example = "1")
        Long matchingRequestId,

        @Schema(description = "Android 화면 전환용 매칭 상태", example = "SEARCHING")
        MatchingStatus matchingStatus,

        @Schema(description = "매칭 요청 DB 상태", example = "REQUESTED")
        MatchingRequestStatus requestStatus
) {

    public static ConsumerMatchingRequestCreateResponse from(MatchingCreationResult result) {
        return new ConsumerMatchingRequestCreateResponse(
                result.matchingRequestId(),
                result.matchingStatus(),
                result.requestStatus()
        );
    }
}
