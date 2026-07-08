package org.sopt.ssingserver.domain.matching.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MatchingStatusTest {

    @Test
    void matchingStatus는_최종_상태값_정의의_Android_표시_상태를_가진다() {
        assertThat(Arrays.stream(MatchingStatus.values())
                .map(Enum::name))
                .containsExactly(
                        "SEARCHING",
                        "NO_AVAILABLE_INSTRUCTOR",
                        "WAITING_FOR_TEAM",
                        "WAITING_FOR_INSTRUCTOR",
                        "WAITING_FOR_CONFIRMATION",
                        "WAITING_FOR_OTHER_CONFIRMATIONS",
                        "PAYMENT_PENDING",
                        "WAITING_FOR_OTHER_PAYMENTS",
                        "CONFIRMED",
                        "REMATCHING",
                        "PAYMENT_EXPIRED",
                        "CANCELED",
                        "FAILED"
                );
    }
}
