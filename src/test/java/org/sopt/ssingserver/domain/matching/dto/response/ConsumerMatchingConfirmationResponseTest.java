package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingConfirmationResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import tools.jackson.databind.ObjectMapper;

class ConsumerMatchingConfirmationResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void from은_결제대기로_전환되면_가격요약을_JSON에_포함한다() throws Exception {
        ConsumerMatchingConfirmationResult result = new ConsumerMatchingConfirmationResult(
                10L,
                MatchingStatus.PAYMENT_PENDING,
                MatchingRequestGroupItemStatus.ACCEPTED,
                MatchingRequestStatus.MATCHED,
                null,
                3L,
                MatchingRequestGroupStatus.PAYMENT_PENDING,
                MatchingRequestGroupItemStatus.ACCEPTED,
                null,
                null,
                null,
                new MatchingPriceSummaryResult(80_000, 20_000, 100_000)
        );

        String json = objectMapper.writeValueAsString(ConsumerMatchingConfirmationResponse.from(result));

        assertThat(json).contains("\"matchingStatus\":\"PAYMENT_PENDING\"");
        assertThat(json).contains("\"lessonPriceAmount\":80000");
        assertThat(json).contains("\"resortPassFeeAmount\":20000");
        assertThat(json).contains("\"totalPaymentAmount\":100000");
    }

    @Test
    void from은_다른_대표소비자_확인대기면_가격요약을_JSON에서_제외한다() throws Exception {
        ConsumerMatchingConfirmationResult result = new ConsumerMatchingConfirmationResult(
                10L,
                MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS,
                MatchingRequestGroupItemStatus.ACCEPTED,
                MatchingRequestStatus.MATCHED,
                null,
                3L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupItemStatus.ACCEPTED,
                1,
                2,
                null,
                null
        );

        String json = objectMapper.writeValueAsString(ConsumerMatchingConfirmationResponse.from(result));

        assertThat(json).contains("\"matchingStatus\":\"WAITING_FOR_OTHER_CONFIRMATIONS\"");
        assertThat(json).doesNotContain("priceSummary");
    }
}
