package org.sopt.ssingserver.domain.lesson.dto.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LessonRealtimeEvent(
        UUID eventId,
        LessonRealtimeEventType eventType,
        OffsetDateTime occurredAt,
        LessonRealtimeRecipientRole recipientRole,
        Long lessonId,
        LessonStatus lessonStatus,
        Object payload
) {

    public record StartConfirmationUpdatedPayload(
            int confirmedCount,
            int requiredCount,
            boolean currentActorConfirmed,
            boolean instructorConfirmed
    ) {
    }
}
