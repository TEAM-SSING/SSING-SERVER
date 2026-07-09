package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingPaymentResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.global.time.AppZoneId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsumerMatchingPaymentResponse(
        @Schema(description = "매칭 요청 ID", example = "10")
        Long matchingRequestId,

        @Schema(description = "Android 화면 전환용 매칭 상태", example = "CONFIRMED")
        MatchingStatus matchingStatus,

        @Schema(description = "현재 요청자의 결제 상태", example = "COMPLETED")
        MatchingRequestPaymentStatus paymentStatus,

        @Schema(description = "매칭 요청 그룹 ID", example = "3")
        Long groupId,

        @Schema(description = "매칭 요청 그룹 상태", example = "CONFIRMED")
        MatchingRequestGroupStatus groupStatus,

        @Schema(description = "결제 완료 요청 수", example = "2")
        int paidCount,

        @Schema(description = "결제가 필요한 요청 수", example = "2")
        int requiredCount,

        @Schema(description = "생성된 강습 ID", example = "30")
        Long lessonId,

        @Schema(description = "결제 단계 만료 시각", example = "2026-06-28T15:33:00+09:00")
        OffsetDateTime expiresAt
) {

    public static ConsumerMatchingPaymentResponse from(ConsumerMatchingPaymentResult result) {
        return new ConsumerMatchingPaymentResponse(
                result.matchingRequestId(),
                result.matchingStatus(),
                result.paymentStatus(),
                result.groupId(),
                result.groupStatus(),
                result.paidCount(),
                result.requiredCount(),
                result.lessonId(),
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
