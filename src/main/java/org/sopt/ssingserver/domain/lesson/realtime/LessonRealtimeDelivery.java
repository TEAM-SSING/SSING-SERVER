package org.sopt.ssingserver.domain.lesson.realtime;

import org.sopt.ssingserver.domain.lesson.dto.realtime.LessonRealtimeEvent;

public record LessonRealtimeDelivery(
        Long recipientMemberId,
        LessonRealtimeEvent event
) {
}
