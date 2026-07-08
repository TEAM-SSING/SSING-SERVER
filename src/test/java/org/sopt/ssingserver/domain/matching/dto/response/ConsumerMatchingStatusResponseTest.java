package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
import tools.jackson.databind.ObjectMapper;

class ConsumerMatchingStatusResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void from은_SEARCHING_응답에서_null_선택필드를_JSON에서_제외한다() throws Exception {
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
                10L,
                MatchingStatus.SEARCHING,
                MatchingRequestStatus.REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        ConsumerMatchingStatusResponse response = ConsumerMatchingStatusResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"matchingRequestId\":10");
        assertThat(json).contains("\"matchingStatus\":\"SEARCHING\"");
        assertThat(json).contains("\"requestStatus\":\"REQUESTED\"");
        assertThat(json).doesNotContain("groupId");
        assertThat(json).doesNotContain("offerStatus");
        assertThat(json).doesNotContain("instructorProfile");
        assertThat(json).doesNotContain("lessonId");
    }

    @Test
    void from은_강사프로필_등급값이_있으면_level까지_JSON에_포함한다() throws Exception {
        MatchingStatusQueryResult result = new MatchingStatusQueryResult(
                10L,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                MatchingRequestStatus.MATCHED,
                null,
                20L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupItemStatus.PENDING,
                MatchingOfferStatus.ACCEPTED,
                null,
                Instant.parse("2026-07-07T00:10:00Z"),
                new MatchingStatusQueryResult.InstructorProfileResult(
                        40L,
                        "김강사",
                        "https://example.com/instructor.png",
                        Gender.FEMALE,
                        1998,
                        3
                ),
                null
        );

        ConsumerMatchingStatusResponse response = ConsumerMatchingStatusResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"instructorId\":40");
        assertThat(json).contains("\"name\":\"김강사\"");
        assertThat(json).contains("\"profileImageUrl\":\"https://example.com/instructor.png\"");
        assertThat(json).contains("\"birthYear\":1998");
        assertThat(json).contains("\"level\":3");
    }
}
