package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.NextMatchingOfferResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstructorMatchingOfferService {

    private final InstructorProfileRepository instructorProfileRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestGroupRepository matchingRequestGroupRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingSearchService matchingSearchService;
    private final MatchingTimeoutPolicy matchingTimeoutPolicy;
    private final Clock clock;

    // 강사 앱 재진입/WebSocket 유실 복구 시 현재 강사에게 노출된 제안만 조회
    @Transactional(readOnly = true)
    public InstructorMatchingOffersResult getCurrentOffers(
            Long memberId,
            int page,
            int size
    ) {
        InstructorProfile instructorProfile = findInstructorProfile(memberId);
        Page<MatchingOffer> matchingOffers = matchingOfferRepository.findByInstructorProfileIdAndStatusOrderByIdAsc(
                instructorProfile.getId(),
                MatchingOfferStatus.OFFERED,
                PageRequest.of(page, size)
        );

        List<MatchingOffer> offerItems = matchingOffers.getContent();
        Map<Long, List<MatchingRequestGroupItem>> groupItemsByGroupId = findGroupItemsByGroupId(offerItems);

        return new InstructorMatchingOffersResult(
                offerItems.stream()
                        .map(matchingOffer -> toItemResult(
                                matchingOffer,
                                groupItemsByGroupId.getOrDefault(
                                        matchingOffer.getMatchingRequestGroup().getId(),
                                        List.of()
                                )
                        ))
                        .toList(),
                matchingOffers.getNumber(),
                matchingOffers.getSize(),
                matchingOffers.hasNext()
        );
    }

    // 제안 row와 그룹 row를 함께 잠그고, 응답 가능 상태를 확인한 뒤 수락/거절 흐름으로 분기
    @Transactional
    public InstructorMatchingOfferDecisionResult respond(
            Long memberId,
            Long offerId,
            MatchingOfferDecision decision
    ) {
        InstructorProfile instructorProfile = findInstructorProfile(memberId);
        MatchingOffer matchingOffer = matchingOfferRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_OFFER_NOT_FOUND));
        validateOfferOwner(instructorProfile, matchingOffer);

        MatchingRequestGroup matchingRequestGroup = matchingRequestGroupRepository.findByIdForUpdate(
                        matchingOffer.getMatchingRequestGroup().getId()
                )
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_GROUP_ALREADY_CLOSED));
        validateRespondable(matchingOffer, matchingRequestGroup);

        List<MatchingRequestGroupItem> groupItems = matchingRequestGroupItemRepository
                .findByMatchingRequestGroupIdForUpdate(matchingRequestGroup.getId());
        if (groupItems.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        Instant now = clock.instant();
        return switch (decision) {
            case ACCEPTED -> accept(matchingOffer, matchingRequestGroup, groupItems, now);
            case REJECTED -> reject(matchingOffer, matchingRequestGroup, groupItems, now);
        };
    }

    // 강사 수락은 그룹을 선점하고 모든 그룹 요청을 대표 소비자 최종 확인 단계로 넘김
    private InstructorMatchingOfferDecisionResult accept(
            MatchingOffer matchingOffer,
            MatchingRequestGroup matchingRequestGroup,
            List<MatchingRequestGroupItem> groupItems,
            Instant now
    ) {
        Instant requesterConfirmationExpiresAt = matchingTimeoutPolicy.requesterConfirmationExpiresAt(now).orElse(null);
        matchingOffer.accept(now);
        matchingRequestGroup.markInstructorAccepted();
        for (MatchingRequestGroupItem groupItem : groupItems) {
            groupItem.requestConfirmation();
            groupItem.getMatchingRequest().markMatched(matchingOffer, requesterConfirmationExpiresAt);
        }

        return new InstructorMatchingOfferDecisionResult(
                matchingOffer.getId(),
                matchingOffer.getStatus(),
                matchingRequestGroup.getId(),
                matchingRequestGroup.getStatus(),
                requesterConfirmationExpiresAt
        );
    }

    // 강사 거절은 해당 제안만 닫고, 같은 요청에서 이미 제안받은 강사를 제외한 다음 후보로 넘김
    private InstructorMatchingOfferDecisionResult reject(
            MatchingOffer matchingOffer,
            MatchingRequestGroup matchingRequestGroup,
            List<MatchingRequestGroupItem> groupItems,
            Instant now
    ) {
        matchingOffer.reject(now);

        MatchingRequest matchingRequest = groupItems.getFirst().getMatchingRequest();
        NextMatchingOfferResult nextOfferResult = matchingSearchService.ensureNextOfferForGroup(
                matchingRequest,
                matchingRequestGroup,
                now
        );
        if (nextOfferResult.hasActiveOffer()) {
            matchingRequestGroup.expose();
        } else {
            closeGroupAndRequestRematch(matchingRequestGroup, groupItems);
        }

        return new InstructorMatchingOfferDecisionResult(
                matchingOffer.getId(),
                matchingOffer.getStatus(),
                matchingRequestGroup.getId(),
                matchingRequestGroup.getStatus(),
                null
        );
    }

    private void closeGroupAndRequestRematch(
            MatchingRequestGroup matchingRequestGroup,
            List<MatchingRequestGroupItem> groupItems
    ) {
        matchingRequestGroup.cancel();
        for (MatchingRequestGroupItem groupItem : groupItems) {
            groupItem.getMatchingRequest().rematchAfterInstructorRejected();
        }
    }

    private Map<Long, List<MatchingRequestGroupItem>> findGroupItemsByGroupId(List<MatchingOffer> matchingOffers) {
        List<Long> groupIds = matchingOffers.stream()
                .map(matchingOffer -> matchingOffer.getMatchingRequestGroup().getId())
                .distinct()
                .toList();
        if (groupIds.isEmpty()) {
            return Map.of();
        }

        return matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByGroupIdAscItemIdAsc(groupIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getMatchingRequestGroup().getId()));
    }

    private InstructorMatchingOffersResult.ItemResult toItemResult(
            MatchingOffer matchingOffer,
            List<MatchingRequestGroupItem> groupItems
    ) {
        if (groupItems.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        MatchingRequest firstRequest = groupItems.getFirst().getMatchingRequest();
        int headcount = groupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .mapToInt(MatchingRequest::getHeadcount)
                .sum();
        Resort resort = firstRequest.getResort();

        InstructorMatchingOffersResult.LessonSummaryResult lessonSummary =
                new InstructorMatchingOffersResult.LessonSummaryResult(
                        new InstructorMatchingOffersResult.ResortResult(
                                resort.getCode(),
                                resort.getDisplayName()
                        ),
                        firstRequest.getSport(),
                        firstRequest.getLessonLevel(),
                        headcount,
                        matchingOffer.getMatchingRequestGroup().getDurationMinutes()
                );

        return new InstructorMatchingOffersResult.ItemResult(
                matchingOffer.getId(),
                matchingOffer.getMatchingRequestGroup().getId(),
                matchingOffer.getStatus(),
                resolveOfferExpiresAt(matchingOffer),
                lessonSummary
        );
    }

    private Instant resolveOfferExpiresAt(MatchingOffer matchingOffer) {
        if (matchingOffer.getStatus() != MatchingOfferStatus.OFFERED) {
            return null;
        }

        return matchingOffer.getExpiresAt();
    }

    private InstructorProfile findInstructorProfile(Long memberId) {
        return instructorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void validateOfferOwner(
            InstructorProfile instructorProfile,
            MatchingOffer matchingOffer
    ) {
        if (!Objects.equals(matchingOffer.getInstructorProfile().getId(), instructorProfile.getId())) {
            throw new BusinessException(MatchingErrorCode.MATCHING_OFFER_NOT_FOUND);
        }
    }

    private void validateRespondable(
            MatchingOffer matchingOffer,
            MatchingRequestGroup matchingRequestGroup
    ) {
        if (matchingOffer.getStatus() != MatchingOfferStatus.OFFERED) {
            throw new BusinessException(MatchingErrorCode.MATCHING_OFFER_ALREADY_RESPONDED);
        }

        if (matchingRequestGroup.getStatus() != MatchingRequestGroupStatus.EXPOSED) {
            throw new BusinessException(MatchingErrorCode.MATCHING_GROUP_ALREADY_CLOSED);
        }
    }
}
