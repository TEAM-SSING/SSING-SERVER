package org.sopt.ssingserver.domain.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCancellationResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

public record ConsumerMatchingCancellationResponse(
        @Schema(description = "매칭 요청 ID", example = "10")
        Long matchingRequestId,

        @Schema(description = "Android 화면 전환용 매칭 상태", example = "CANCELED")
        MatchingStatus matchingStatus,

        @Schema(description = "매칭 요청 DB 상태", example = "CANCELED")
        MatchingRequestStatus requestStatus,

        @Schema(description = "매칭 요청 상태 변경 사유", example = "CONSUMER_CANCELED")
        MatchingRequestStatusReason requestStatusReason,

        @Schema(description = "취소 시각", example = "2026-06-28T15:31:00+09:00")
        OffsetDateTime canceledAt
) {

    private static final ZoneOffset KOREA_TIME_OFFSET = ZoneOffset.ofHours(9);

    public static ConsumerMatchingCancellationResponse from(MatchingCancellationResult result) {
        return new ConsumerMatchingCancellationResponse(
                result.matchingRequestId(),
                result.matchingStatus(),
                result.requestStatus(),
                result.requestStatusReason(),
                result.canceledAt().atOffset(KOREA_TIME_OFFSET)
        );
    }
}
