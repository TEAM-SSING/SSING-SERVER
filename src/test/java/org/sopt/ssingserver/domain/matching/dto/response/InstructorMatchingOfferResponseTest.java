package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import tools.jackson.databind.ObjectMapper;

class InstructorMatchingOfferResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void currentOffers는_제안상태가_OFFERED가_아니어서_expiresAt이_null이면_JSON에서_제외한다() throws Exception {
        InstructorMatchingOffersResult result = new InstructorMatchingOffersResult(
                List.of(new InstructorMatchingOffersResult.ItemResult(
                        21L,
                        3L,
                        MatchingOfferStatus.REJECTED,
                        null,
                        lessonSummary()
                )),
                0,
                20,
                false
        );

        InstructorMatchingOffersResponse response = InstructorMatchingOffersResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"offerId\":21");
        assertThat(json).contains("\"offerStatus\":\"REJECTED\"");
        assertThat(json).doesNotContain("expiresAt");
    }

    @Test
    void currentOffers는_expiresAt이_있으면_JSON에_포함한다() throws Exception {
        InstructorMatchingOffersResult result = new InstructorMatchingOffersResult(
                List.of(new InstructorMatchingOffersResult.ItemResult(
                        21L,
                        3L,
                        MatchingOfferStatus.OFFERED,
                        Instant.parse("2026-07-07T00:01:00Z"),
                        lessonSummary()
                )),
                0,
                20,
                false
        );

        InstructorMatchingOffersResponse response = InstructorMatchingOffersResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"offerStatus\":\"OFFERED\"");
        assertThat(json).contains("expiresAt");
        assertThat(json).contains("2026-07-07T00:01:00Z");
    }

    @Test
    void decisionResponse는_거절시_requesterConfirmationExpiresAt을_JSON에서_제외한다() throws Exception {
        InstructorMatchingOfferDecisionResult result = new InstructorMatchingOfferDecisionResult(
                21L,
                MatchingOfferStatus.REJECTED,
                3L,
                MatchingRequestGroupStatus.EXPOSED,
                null
        );

        InstructorMatchingOfferDecisionResponse response = InstructorMatchingOfferDecisionResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"offerStatus\":\"REJECTED\"");
        assertThat(json).contains("\"groupStatus\":\"EXPOSED\"");
        assertThat(json).doesNotContain("requesterConfirmationExpiresAt");
    }

    private InstructorMatchingOffersResult.LessonSummaryResult lessonSummary() {
        return new InstructorMatchingOffersResult.LessonSummaryResult(
                new InstructorMatchingOffersResult.ResortResult("HIGH1", "하이원"),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                4,
                120
        );
    }
}
