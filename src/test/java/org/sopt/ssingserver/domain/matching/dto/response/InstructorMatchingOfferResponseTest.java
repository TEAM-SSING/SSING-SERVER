package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
import tools.jackson.databind.ObjectMapper;

class InstructorMatchingOfferResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void currentOffers는_I07_복구에필요한_요청자_강습_가격요약을_JSON으로_반환한다() throws Exception {
        InstructorMatchingOffersResult result = new InstructorMatchingOffersResult(
                List.of(new InstructorMatchingOffersResult.ItemResult(
                        21L,
                        3L,
                        MatchingOfferStatus.REJECTED,
                        null,
                        requestSummary(),
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
        assertThat(json).contains("\"requesterName\":\"홍길동\"");
        assertThat(json).contains("\"headcount\":2");
        assertThat(json).contains("\"matchingRequestCount\":1");
        assertThat(json).contains("\"resort\":{\"code\":\"HIGH1\",\"displayName\":\"하이원\"}");
        assertThat(json).contains("\"sport\":\"SNOWBOARD\"");
        assertThat(json).contains("\"level\":\"FIRST_TIME\"");
        assertThat(json).contains("\"durationMinutes\":120");
        assertThat(json).contains("\"totalHeadcount\":2");
        assertThat(json).contains("\"startType\":\"IMMEDIATE\"");
        assertThat(json).doesNotContain("participants");
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
                        requestSummary(),
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
    void offerDetail은_현재활성협상의_화면상태를_반환하고_무기한대기만료필드는_포함하지_않는다() throws Exception {
        InstructorMatchingOfferDetailResult result = new InstructorMatchingOfferDetailResult(
                21L,
                3L,
                MatchingOfferStatus.ACCEPTED,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                requestSummary(),
                lessonSummary(),
                priceSummary(),
                List.of(
                        new InstructorMatchingOfferDetailResult.ParticipantResult(10, Gender.MALE),
                        new InstructorMatchingOfferDetailResult.ParticipantResult(12, Gender.FEMALE)
                )
        );

        String json = objectMapper.writeValueAsString(InstructorMatchingOfferDetailResponse.from(result));

        assertThat(json).contains("\"offerStatus\":\"ACCEPTED\"");
        assertThat(json).contains("\"groupStatus\":\"INSTRUCTOR_ACCEPTED\"");
        assertThat(json).contains("\"matchingStatus\":\"WAITING_FOR_CONFIRMATION\"");
        assertThat(json).contains("\"requesterName\":\"홍길동\"");
        assertThat(json).contains("\"participants\":[{\"age\":10,\"gender\":\"MALE\"},{\"age\":12,\"gender\":\"FEMALE\"}]");
        assertThat(json).doesNotContain("participantId");
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
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                120,
                2,
                "IMMEDIATE"
        );
    }

    private InstructorMatchingOffersResult.RequestSummaryResult requestSummary() {
        return new InstructorMatchingOffersResult.RequestSummaryResult("홍길동", 2, 1);
    }

    private MatchingPriceSummaryResult priceSummary() {
        return new MatchingPriceSummaryResult(80_000, 20_000, 100_000);
    }
}
