package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.error.InstructorErrorCode;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDetailResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.NextMatchingOfferResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.event.InstructorAcceptedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedReason;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstructorMatchingOfferService {

    private static final String IMMEDIATE_START_TYPE = "IMMEDIATE";

    private final InstructorProfileRepository instructorProfileRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestGroupRepository matchingRequestGroupRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingRequestParticipantRepository matchingRequestParticipantRepository;
    private final MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;
    private final MatchingSearchService matchingSearchService;
    private final MatchingTimeoutPolicy matchingTimeoutPolicy;
    private final MatchingEventDispatcher matchingEventDispatcher;
    private final Clock clock;

    // 홈 카드 진입 시 새 제안과 저장된 대기 조건을 함께 조회해 REST 기준 화면을 복구한다.
    @Transactional(readOnly = true)
    public InstructorMatchingOffersResult getCurrentOffers(Long memberId) {
        InstructorProfile instructorProfile = findInstructorProfile(memberId);
        List<Long> activeOfferIds = matchingOfferRepository
                .findIdsByInstructorProfileIdAndStatusAndGroupStatusOrderByIdAsc(
                        instructorProfile.getId(),
                        MatchingOfferStatus.OFFERED,
                        MatchingRequestGroupStatus.EXPOSED,
                        PageRequest.of(0, 2)
                );
        if (activeOfferIds.size() > 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        Optional<InstructorMatchingSetting> matchingSettingOptional = instructorMatchingSettingRepository
                .findByInstructorProfileId(instructorProfile.getId());
        if (matchingSettingOptional.isEmpty()) {
            if (!activeOfferIds.isEmpty()) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
            throw new BusinessException(MatchingErrorCode.MATCHING_NOT_ACTIVE);
        }

        InstructorMatchingSetting matchingSetting = matchingSettingOptional.get();
        if (activeOfferIds.isEmpty() && !matchingSetting.isExposed()) {
            throw new BusinessException(MatchingErrorCode.MATCHING_NOT_ACTIVE);
        }

        return new InstructorMatchingOffersResult(
                activeOfferIds.isEmpty() ? null : activeOfferIds.getFirst(),
                toMatchingSettingResult(instructorProfile, matchingSetting)
        );
    }

    // LAZY 저장 조건을 트랜잭션 안에서 응답 값으로 복사하고, Set 순서를 API 계약에 맞게 고정한다.
    private InstructorMatchingOffersResult.MatchingSettingResult toMatchingSettingResult(
            InstructorProfile instructorProfile,
            InstructorMatchingSetting matchingSetting
    ) {
        Resort resort = Optional.ofNullable(instructorProfile.getResort())
                .orElseThrow(() -> new BusinessException(InstructorErrorCode.INSTRUCTOR_RESORT_NOT_SET));

        return new InstructorMatchingOffersResult.MatchingSettingResult(
                matchingSetting.isExposed(),
                new InstructorMatchingOffersResult.ResortResult(
                        resort.getCode(),
                        resort.getDisplayName()
                ),
                matchingSetting.getSport(),
                matchingSetting.getLessonLevels().stream()
                        .sorted()
                        .toList(),
                matchingSetting.getAvailableDurationMinutes().stream()
                        .sorted()
                        .toList(),
                matchingSetting.getMaxHeadcount(),
                matchingSetting.isEquipmentReady()
        );
    }

    // 제안 존재·소유권을 먼저 확인한 뒤 진행 중인 협상만 상세 화면으로 복구한다.
    @Transactional(readOnly = true)
    public InstructorMatchingOfferDetailResult getOfferDetail(
            Long memberId,
            Long offerId
    ) {
        InstructorProfile instructorProfile = findInstructorProfile(memberId);
        MatchingOffer matchingOffer = matchingOfferRepository.findDetailById(offerId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_OFFER_NOT_FOUND));
        validateOfferOwner(instructorProfile, matchingOffer);
        Optional<MatchingStatus> matchingStatus = resolveRecoveryMatchingStatus(matchingOffer);
        if (matchingStatus.isEmpty()) {
            if (isClosedMatching(matchingOffer)) {
                throw new BusinessException(MatchingErrorCode.MATCHING_NOT_ACTIVE);
            }
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        List<MatchingRequestGroupItem> groupItems = matchingRequestGroupItemRepository
                .findByMatchingRequestGroupIdOrderByIdAsc(matchingOffer.getMatchingRequestGroup().getId());
        MatchingOfferPriceSnapshot priceSnapshot = matchingOfferPriceSnapshotRepository
                .findByMatchingOfferId(matchingOffer.getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
        InstructorMatchingOffersResult.ItemResult item = toItemResult(
                matchingOffer,
                groupItems,
                priceSnapshot
        );
        List<InstructorMatchingOfferDetailResult.ParticipantResult> participants = findParticipants(groupItems);

        return InstructorMatchingOfferDetailResult.available(
                item.offerId(),
                item.groupId(),
                item.offerStatus(),
                matchingOffer.getMatchingRequestGroup().getStatus(),
                matchingStatus.get(),
                item.requestSummary(),
                item.lessonSummary(),
                item.priceSummary(),
                participants
        );
    }

    // 제안 row와 그룹 row를 함께 잠그고, 응답 가능 상태를 확인한 뒤 수락/거절 흐름으로 분기
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        matchingEventDispatcher.publishAfterCommit(new InstructorAcceptedEvent(
                UUID.randomUUID(),
                now,
                matchingRequestGroup.getId(),
                matchingOffer.getId()
        ));

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
        matchingEventDispatcher.publishAfterCommit(new MatchingOfferClosedEvent(
                UUID.randomUUID(),
                now,
                matchingRequestGroup.getId(),
                matchingOffer.getId(),
                MatchingOfferClosedReason.REJECTED
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
            List<MatchingRequestGroupItem> groupItems,
            Instant now
    ) {
        matchingRequestGroup.cancel();
        for (MatchingRequestGroupItem groupItem : groupItems) {
            MatchingRequest matchingRequest = groupItem.getMatchingRequest();
            matchingRequest.rematchAfterInstructorRejected();
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

    private InstructorMatchingOffersResult.ItemResult toItemResult(
            MatchingOffer matchingOffer,
            List<MatchingRequestGroupItem> groupItems,
            MatchingOfferPriceSnapshot priceSnapshot
    ) {
        if (groupItems.isEmpty() || priceSnapshot == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        MatchingRequest firstRequest = groupItems.getFirst().getMatchingRequest();
        Resort resort = firstRequest.getResort();
        int totalHeadcount = groupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .mapToInt(MatchingRequest::getHeadcount)
                .sum();

        InstructorMatchingOffersResult.RequestSummaryResult requestSummary =
                new InstructorMatchingOffersResult.RequestSummaryResult(
                        firstRequest.getMember().getNickname(),
                        firstRequest.getHeadcount(),
                        groupItems.size()
                );

        InstructorMatchingOffersResult.LessonSummaryResult lessonSummary =
                new InstructorMatchingOffersResult.LessonSummaryResult(
                        new InstructorMatchingOffersResult.ResortResult(
                                resort.getCode(),
                                resort.getDisplayName()
                        ),
                        firstRequest.getSport(),
                        firstRequest.getLessonLevel(),
                        matchingOffer.getMatchingRequestGroup().getDurationMinutes(),
                        totalHeadcount,
                        IMMEDIATE_START_TYPE
                );

        return new InstructorMatchingOffersResult.ItemResult(
                matchingOffer.getId(),
                matchingOffer.getMatchingRequestGroup().getId(),
                matchingOffer.getStatus(),
                resolveOfferExpiresAt(matchingOffer),
                requestSummary,
                lessonSummary,
                MatchingPriceSummaryResult.from(priceSnapshot)
        );
    }

    // 그룹 전체 참여자를 한 번에 읽고 I07 상세 복구에 필요한 나이/성별만 남긴다.
    private List<InstructorMatchingOfferDetailResult.ParticipantResult> findParticipants(
            List<MatchingRequestGroupItem> groupItems
    ) {
        List<Long> matchingRequestIds = groupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .map(MatchingRequest::getId)
                .distinct()
                .toList();

        return matchingRequestParticipantRepository
                .findByMatchingRequestIdInOrderByMatchingRequestIdAscIdAsc(matchingRequestIds)
                .stream()
                .map(InstructorMatchingOfferService::toParticipantResult)
                .toList();
    }

    private static InstructorMatchingOfferDetailResult.ParticipantResult toParticipantResult(
            MatchingRequestParticipant participant
    ) {
        return new InstructorMatchingOfferDetailResult.ParticipantResult(
                participant.getAge(),
                participant.getGender()
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

    // offer/group 상태 조합을 한곳에서 매핑해 복구 가능 여부와 화면 상태가 어긋나지 않게 한다.
    private Optional<MatchingStatus> resolveRecoveryMatchingStatus(MatchingOffer matchingOffer) {
        MatchingOfferStatus offerStatus = matchingOffer.getStatus();
        MatchingRequestGroupStatus groupStatus = matchingOffer.getMatchingRequestGroup().getStatus();

        if (offerStatus == MatchingOfferStatus.OFFERED
                && groupStatus == MatchingRequestGroupStatus.EXPOSED) {
            return Optional.of(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        }
        if (offerStatus != MatchingOfferStatus.ACCEPTED) {
            return Optional.empty();
        }

        return switch (groupStatus) {
            case INSTRUCTOR_ACCEPTED -> Optional.of(MatchingStatus.WAITING_FOR_CONFIRMATION);
            case PAYMENT_PENDING -> Optional.of(MatchingStatus.PAYMENT_PENDING);
            default -> Optional.empty();
        };
    }

    // 정상 종료만 409로 분류하고, 내부 중간 상태나 불가능한 조합은 호출부의 500 경로로 남긴다.
    private boolean isClosedMatching(MatchingOffer matchingOffer) {
        return switch (matchingOffer.getStatus()) {
            case REJECTED, CANCELED, EXPIRED -> true;
            case OFFERED, ACCEPTED -> isClosedGroupStatus(matchingOffer.getMatchingRequestGroup().getStatus());
        };
    }

    private boolean isClosedGroupStatus(MatchingRequestGroupStatus groupStatus) {
        return switch (groupStatus) {
            case PAYMENT_EXPIRED, CONFIRMED, LOST, CANCELED, EXPIRED -> true;
            case CANDIDATE, EXPOSED, INSTRUCTOR_ACCEPTED, CONSUMER_ACCEPTED, PAYMENT_PENDING -> false;
        };
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
