package org.sopt.ssingserver.domain.matching.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MatchingRequestStatusTest {

    @Test
    void 활성_매칭_협상은_REQUESTED_GROUPED_MATCHED만_포함한다() {
        assertThat(Arrays.stream(MatchingRequestStatus.values())
                .filter(MatchingRequestStatus::isActiveNegotiation))
                .containsExactly(
                        MatchingRequestStatus.REQUESTED,
                        MatchingRequestStatus.GROUPED,
                        MatchingRequestStatus.MATCHED
                );
    }

    @Test
    void 확정과_종료_상태는_활성_매칭_협상에서_제외한다() {
        assertThat(Arrays.stream(MatchingRequestStatus.values())
                .filter(status -> !status.isActiveNegotiation()))
                .containsExactly(
                        MatchingRequestStatus.CONFIRMED,
                        MatchingRequestStatus.COMPLETED,
                        MatchingRequestStatus.CANCELED,
                        MatchingRequestStatus.EXPIRED,
                        MatchingRequestStatus.FAILED
                );
    }
}
