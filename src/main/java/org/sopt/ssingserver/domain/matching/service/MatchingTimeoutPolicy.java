package org.sopt.ssingserver.domain.matching.service;

import java.time.Instant;
import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.springframework.stereotype.Component;

// 매칭 시간 정책의 단일 진입점. 현 정책은 모든 사용자 행동 대기를 무기한으로 둔다.
@Component
public class MatchingTimeoutPolicy {

    public Optional<Instant> instructorOfferExpiresAt(Instant exposedAt) {
        return Optional.empty();
    }

    public Optional<Instant> requesterConfirmationExpiresAt(Instant acceptedAt) {
        return Optional.empty();
    }

    public Optional<Instant> matchingStatusExpiresAt(
            MatchingStatus matchingStatus,
            MatchingRequest matchingRequest,
            Optional<MatchingOffer> matchingOffer,
            Optional<MatchingRequestPayment> matchingRequestPayment
    ) {
        return Optional.empty();
    }

    public boolean shouldRunInstructorOfferExpiration() {
        return false;
    }
}
