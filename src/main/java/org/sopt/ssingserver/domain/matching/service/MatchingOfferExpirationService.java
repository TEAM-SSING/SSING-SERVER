package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dto.result.NextMatchingOfferResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingOfferExpirationService {

    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestGroupRepository matchingRequestGroupRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingSearchService matchingSearchService;
    private final MatchingEventDispatcher matchingEventDispatcher;
    private final Clock clock;

    // 유한 응답 시간 정책 재도입 시 OFFERED 제안 하나를 만료 처리하고 다음 우선순위 강사에게 넘기는 구현체
    @Transactional
    public void expireOffer(Long matchingOfferId) {
        Instant now = clock.instant();
        Optional<MatchingOffer> offerToExpire = matchingOfferRepository.findByIdForUpdate(matchingOfferId)
                .filter(matchingOffer -> matchingOffer.getStatus() == MatchingOfferStatus.OFFERED)
                .filter(matchingOffer -> matchingOffer.isExpired(now));
        if (offerToExpire.isEmpty()) {
            return;
        }
        MatchingOffer matchingOffer = offerToExpire.get();

        MatchingRequestGroup matchingRequestGroup = matchingRequestGroupRepository.findByIdForUpdate(
                        matchingOffer.getMatchingRequestGroup().getId()
                )
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
        List<MatchingRequestGroupItem> groupItems = matchingRequestGroupItemRepository
                .findByMatchingRequestGroupIdForUpdate(matchingRequestGroup.getId());
        if (groupItems.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        matchingOffer.expire();
        matchingEventDispatcher.publishAfterCommit(new MatchingOfferClosedEvent(
                UUID.randomUUID(),
                now,
                matchingRequestGroup.getId(),
                matchingOffer.getId(),
                MatchingOfferClosedReason.EXPIRED
        ));
        MatchingRequest matchingRequest = groupItems.getFirst().getMatchingRequest();
        NextMatchingOfferResult nextOfferResult = matchingSearchService.ensureNextOfferForGroup(
                matchingRequest,
                matchingRequestGroup,
                now
        );
        if (nextOfferResult.hasActiveOffer()) {
            matchingRequestGroup.expose();
        } else {
            closeGroupAndRequestRematch(matchingRequestGroup, groupItems, now);
        }
    }

    private void closeGroupAndRequestRematch(
            MatchingRequestGroup matchingRequestGroup,
            List<MatchingRequestGroupItem> groupItems,
            Instant now
    ) {
        matchingRequestGroup.expire();
        for (MatchingRequestGroupItem groupItem : groupItems) {
            MatchingRequest matchingRequest = groupItem.getMatchingRequest();
            matchingRequest.rematchAfterInstructorTimeout();
            matchingEventDispatcher.publishAfterCommit(new MatchingRequestStatusChangedEvent(
                    UUID.randomUUID(),
                    now,
                    matchingRequest.getId(),
                    matchingRequestGroup.getId(),
                    matchingRequest.getStatus(),
                    matchingRequest.getStatusReason(),
                    MatchingStatus.REMATCHING
            ));
        }
    }

}
