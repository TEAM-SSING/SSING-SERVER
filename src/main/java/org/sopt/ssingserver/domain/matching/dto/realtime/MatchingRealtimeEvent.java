package org.sopt.ssingserver.domain.matching.dto.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
// STOMP 메시지는 REST BaseResponse가 아니라 eventId/eventType/payload를 가진 이벤트 envelope로 보낸다.
public record MatchingRealtimeEvent(
        UUID eventId,
        MatchingRealtimeEventType eventType,
        OffsetDateTime occurredAt,
        MatchingRealtimeRecipientRole recipientRole,
        Long matchingRequestId,
        Long groupId,
        Long offerId,
        MatchingStatus matchingStatus,
        Object payload
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MatchingStatusPayload(
            String message,
            MatchingRequestStatusReason requestStatusReason
    ) {
    }

    public record MatchingOfferReceivedPayload(
            RequestSummary requestSummary,
            LessonSummary lessonSummary
    ) {
    }

    public record MatchingOfferClosedPayload(
            String closedReason,
            String message
    ) {
    }

    public record InstructorAcceptedPayload(
            InstructorSummary instructor,
            LessonSummary lessonSummary
    ) {
    }

    public record RequesterConfirmationUpdatedPayload(
            ProgressSummary progressSummary
    ) {
    }

    public record PaymentPendingPayload(
            Long matchingRequestPaymentId
    ) {
    }

    public record PaymentStatusChangedPayload(
            ProgressSummary progressSummary
    ) {
    }

    public record MatchingConfirmedPayload(
            Long lessonId,
            LessonSummary lessonSummary
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstructorSummary(
            Long instructorProfileId,
            String name,
            String profileImageUrl
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProgressSummary(
            Integer acceptedRequesterCount,
            int totalRequesterCount,
            Integer paidRequesterCount
    ) {
    }

    public record RequestSummary(
            String requesterName,
            int headcount,
            int matchingRequestCount
    ) {
    }

    public record LessonSummary(
            String resortName,
            String sport,
            String level,
            int durationMinutes,
            int totalHeadcount,
            String startType
    ) {
    }
}
