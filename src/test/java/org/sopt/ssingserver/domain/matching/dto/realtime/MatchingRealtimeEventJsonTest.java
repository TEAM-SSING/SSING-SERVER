package org.sopt.ssingserver.domain.matching.dto.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.InstructorAcceptedPayload;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.InstructorSummary;
import org.sopt.ssingserver.domain.matching.dto.realtime.MatchingRealtimeEvent.LessonSummary;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import tools.jackson.databind.ObjectMapper;

class MatchingRealtimeEventJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void WebSocket_JSON은_REST_wrapper없이_offset과_필수필드만_직렬화한다() {
        MatchingRealtimeEvent event = new MatchingRealtimeEvent(
                UUID.fromString("9f3c2b0e-3d4b-4b58-8e5a-3eaf4c7e8a01"),
                MatchingRealtimeEventType.INSTRUCTOR_ACCEPTED,
                OffsetDateTime.parse("2026-07-07T09:00:00+09:00"),
                MatchingRealtimeRecipientRole.CONSUMER,
                10L,
                20L,
                null,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                new InstructorAcceptedPayload(
                        new InstructorSummary(40L, "김강사", null),
                        new LessonSummary("하이원", "SNOWBOARD", "FIRST_TIME", 120, 4, "IMMEDIATE")
                )
        );

        String json = objectMapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"eventId\":\"9f3c2b0e-3d4b-4b58-8e5a-3eaf4c7e8a01\"")
                .contains("\"eventType\":\"INSTRUCTOR_ACCEPTED\"")
                .contains("\"occurredAt\":\"2026-07-07T09:00:00+09:00\"")
                .contains("\"recipientRole\":\"CONSUMER\"")
                .contains("\"matchingRequestId\":10")
                .contains("\"matchingStatus\":\"WAITING_FOR_CONFIRMATION\"")
                .contains("\"payload\"")
                .contains("\"instructorProfileId\":40")
                .doesNotContain("\"offerId\"")
                .doesNotContain("\"profileImageUrl\"")
                .doesNotContain("\"success\"")
                .doesNotContain("\"data\"")
                .doesNotContain("\"requestId\"");
    }
}
