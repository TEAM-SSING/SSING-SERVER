package org.sopt.ssingserver.domain.matching.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCancellationResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

class ConsumerMatchingCancellationResponseTest {

    @Test
    void from은_취소결과를_API응답으로_변환하고_취소시각을_한국시간_offset으로_내린다() {
        MatchingCancellationResult result = new MatchingCancellationResult(
                10L,
                MatchingStatus.CANCELED,
                MatchingRequestStatus.CANCELED,
                MatchingRequestStatusReason.CONSUMER_CANCELED,
                Instant.parse("2026-07-07T00:03:05Z")
        );

        ConsumerMatchingCancellationResponse response = ConsumerMatchingCancellationResponse.from(result);

        assertThat(response.matchingRequestId()).isEqualTo(10L);
        assertThat(response.matchingStatus()).isSameAs(MatchingStatus.CANCELED);
        assertThat(response.requestStatus()).isSameAs(MatchingRequestStatus.CANCELED);
        assertThat(response.requestStatusReason()).isSameAs(MatchingRequestStatusReason.CONSUMER_CANCELED);
        assertThat(response.canceledAt())
                .isEqualTo(OffsetDateTime.of(2026, 7, 7, 9, 3, 5, 0, ZoneOffset.ofHours(9)));
    }
}
