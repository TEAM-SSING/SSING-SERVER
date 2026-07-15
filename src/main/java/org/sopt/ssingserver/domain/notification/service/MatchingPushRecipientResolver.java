package org.sopt.ssingserver.domain.notification.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchingPushRecipientResolver {

    private final MatchingOfferRepository matchingOfferRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> findInstructorMemberId(Long matchingOfferId) {
        // 커밋된 제안의 강사 회원 ID를 별도 읽기 트랜잭션에서 조회해 알림 수신자를 확정한다.
        return matchingOfferRepository.findRealtimeContextById(matchingOfferId)
                .map(matchingOffer -> matchingOffer.getInstructorProfile().getMember().getId());
    }
}
