package org.sopt.ssingserver.domain.matching.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;

class MatchingRequestGroupTest {

    @Test
    void createCandidate는_후보그룹으로_초기화한다() {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate();

        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.CANDIDATE);
    }

    @Test
    void 상태변경_메서드는_의도에_맞는_그룹상태를_저장한다() {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate();

        group.expose();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);

        group.markInstructorAccepted();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED);

        group.markConsumerAccepted();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.CONSUMER_ACCEPTED);

        group.markPaymentPending();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.PAYMENT_PENDING);

        group.markPaymentExpired();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.PAYMENT_EXPIRED);

        group.confirm();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.CONFIRMED);

        group.lose();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.LOST);

        group.cancel();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.CANCELED);

        group.expire();
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPIRED);
    }
}
