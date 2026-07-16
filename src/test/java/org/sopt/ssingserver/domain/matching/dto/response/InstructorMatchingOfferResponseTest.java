package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;
import tools.jackson.databind.ObjectMapper;

class InstructorMatchingOfferResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void currentOffers는_새_제안이_있어도_offerId와_저장된_대기조건만_JSON으로_반환한다() throws Exception {
        InstructorMatchingOffersResult result = new InstructorMatchingOffersResult(
                21L,
                matchingSetting()
        );

        String json = objectMapper.writeValueAsString(InstructorMatchingOffersResponse.from(result));

        assertThat(json).contains("\"offerId\":21");
        assertThat(json).contains("\"matchingSetting\":{");
        assertThat(json).contains("\"isExposed\":true");
        assertThat(json).doesNotContain(
                "items",
                "currentPage",
                "size",
                "hasNext",
                "activeOffer",
                "requestSummary",
                "lessonSummary",
                "priceSummary"
        );
    }

    @Test
    void currentOffers는_제안이_없으면_offerId_null과_저장된_대기조건을_JSON으로_반환한다() throws Exception {
        InstructorMatchingOffersResult result = new InstructorMatchingOffersResult(
                null,
                matchingSetting()
        );

        String json = objectMapper.writeValueAsString(InstructorMatchingOffersResponse.from(result));

        assertThat(json).contains("\"offerId\":null");
        assertThat(json).contains("\"isExposed\":true");
        assertThat(json).contains("\"resort\":{\"code\":\"HIGH1\",\"displayName\":\"하이원\"}");
        assertThat(json).contains("\"sport\":\"SNOWBOARD\"");
        assertThat(json).contains("\"lessonLevels\":[\"FIRST_TIME\",\"INTERMEDIATE\"]");
        assertThat(json).contains("\"availableDurationMinutes\":[120,240]");
        assertThat(json).contains("\"maxHeadcount\":3");
        assertThat(json).contains("\"equipmentReady\":true");
        assertThat(json).contains(
                "\"pricePolicy\":{\"baseDurationMinutes\":120,"
                        + "\"basePriceAmount\":60000,\"additionalPersonPriceAmount\":20000}"
        );
        assertThat(json).doesNotContain("items", "currentPage", "size", "hasNext", "activeOffer");
    }

    @Test
    void offerDetail은_현재활성협상의_화면상태를_반환하고_무기한대기만료필드는_포함하지_않는다() throws Exception {
        InstructorMatchingOfferDetailResult result = InstructorMatchingOfferDetailResult.available(
                21L,
                3L,
                MatchingOfferStatus.ACCEPTED,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                requestSummary(),
                lessonSummary(),
                new InstructorPriceSummaryResult(80_000),
                List.of(
                        new InstructorMatchingOfferDetailResult.ParticipantResult(10, Gender.MALE),
                        new InstructorMatchingOfferDetailResult.ParticipantResult(12, Gender.FEMALE)
                )
        );

        String json = objectMapper.writeValueAsString(InstructorMatchingOfferDetailResponse.from(result));

        assertThat(json).contains("\"recoveryState\":\"AVAILABLE\"");
        assertThat(json).contains("\"offerStatus\":\"ACCEPTED\"");
        assertThat(json).contains("\"groupStatus\":\"INSTRUCTOR_ACCEPTED\"");
        assertThat(json).contains("\"matchingStatus\":\"WAITING_FOR_CONFIRMATION\"");
        assertThat(json).contains("\"requesterName\":\"홍길동\"");
        assertThat(json).contains("\"priceSummary\":{\"instructorSettlementAmount\":80000}");
        assertThat(json).doesNotContain("lessonPriceAmount", "resortPassFeeAmount", "totalPaymentAmount");
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

    private InstructorMatchingOffersResult.MatchingSettingResult matchingSetting() {
        return new InstructorMatchingOffersResult.MatchingSettingResult(
                true,
                new InstructorMatchingOffersResult.ResortResult("HIGH1", "하이원"),
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.INTERMEDIATE),
                List.of(120, 240),
                3,
                true,
                new InstructorMatchingOffersResult.PricePolicyResult(60_000, 20_000)
        );
    }
}
