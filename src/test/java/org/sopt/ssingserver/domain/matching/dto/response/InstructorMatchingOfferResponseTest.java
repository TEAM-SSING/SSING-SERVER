package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
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
                        lessonSummary(),
                        priceSummary()
                )),
                0,
                20,
                false
        );

        InstructorMatchingOffersResponse response = InstructorMatchingOffersResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"offerId\":21");
        assertThat(json).contains("\"offerStatus\":\"REJECTED\"");
        assertThat(json).contains("\"lessonPriceAmount\":80000");
        assertThat(json).contains("\"resortPassFeeAmount\":20000");
        assertThat(json).contains("\"totalPaymentAmount\":100000");
        assertThat(json).contains("\"resort\":{\"code\":\"HIGH1\",\"displayName\":\"하이원\"}");
        assertThat(json).contains("\"sport\":\"SNOWBOARD\"");
        assertThat(json).doesNotContain("lessonLevel", "headcount", "durationMinutes");
        assertThat(json).doesNotContain("expiresAt");
    }

    @Test
    void currentOffers는_OFFERED여도_expiresAt이_null이면_JSON에서_제외한다() throws Exception {
        InstructorMatchingOffersResult result = new InstructorMatchingOffersResult(
                List.of(new InstructorMatchingOffersResult.ItemResult(
                        21L,
                        3L,
                        MatchingOfferStatus.OFFERED,
                        null,
                        lessonSummary(),
                        priceSummary()
                )),
                0,
                20,
                false
        );

        InstructorMatchingOffersResponse response = InstructorMatchingOffersResponse.from(result);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"offerStatus\":\"OFFERED\"");
        assertThat(json).doesNotContain("expiresAt");
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
                Sport.SNOWBOARD
        );
    }

    private MatchingPriceSummaryResult priceSummary() {
        return new MatchingPriceSummaryResult(80_000, 20_000, 100_000);
    }
}
