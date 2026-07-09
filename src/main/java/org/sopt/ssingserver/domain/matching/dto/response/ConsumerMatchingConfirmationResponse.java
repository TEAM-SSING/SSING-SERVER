package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingConfirmationResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.global.time.AppZoneId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsumerMatchingConfirmationResponse(
        @Schema(description = "매칭 요청 ID", example = "10")
        Long matchingRequestId,

        @Schema(description = "Android 화면 전환용 매칭 상태", example = "PAYMENT_PENDING")
        MatchingStatus matchingStatus,

        @Schema(description = "현재 요청자의 최종 응답 상태", example = "ACCEPTED")
        MatchingRequestGroupItemStatus confirmationStatus,

        @Schema(description = "매칭 요청 DB 상태", example = "MATCHED")
        MatchingRequestStatus requestStatus,

        @Schema(description = "매칭 요청 상태 변경 사유", example = "CONSUMER_REJECTED_INSTRUCTOR")
        MatchingRequestStatusReason requestStatusReason,

        @Schema(description = "매칭 요청 그룹 ID", example = "3")
        Long groupId,

        @Schema(description = "매칭 요청 그룹 상태", example = "PAYMENT_PENDING")
        MatchingRequestGroupStatus groupStatus,

        @Schema(description = "현재 요청자의 그룹 항목 상태", example = "ACCEPTED")
        MatchingRequestGroupItemStatus itemStatus,

        @Schema(description = "최종 수락한 대표 소비자 수", example = "1")
        Integer confirmedCount,

        @Schema(description = "필요한 대표 소비자 수", example = "2")
        Integer requiredCount,

        @Schema(description = "현재 단계 만료 시각", example = "2026-06-28T15:33:00+09:00")
        OffsetDateTime expiresAt
) {

    public static ConsumerMatchingConfirmationResponse from(ConsumerMatchingConfirmationResult result) {
        return new ConsumerMatchingConfirmationResponse(
                result.matchingRequestId(),
                result.matchingStatus(),
                result.confirmationStatus(),
                result.requestStatus(),
                result.requestStatusReason(),
                result.groupId(),
                result.groupStatus(),
                result.itemStatus(),
                result.confirmedCount(),
                result.requiredCount(),
                toOffsetDateTime(result.expiresAt())
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        return instant.atZone(AppZoneId.SEOUL).toOffsetDateTime();
    }
}
