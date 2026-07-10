package org.sopt.ssingserver.domain.matching.service;

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingPriceSummaryResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingStatusQueryService {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final LessonRepository lessonRepository;
    private final MatchingStatusResolver matchingStatusResolver;
    private final MatchingTimeoutPolicy matchingTimeoutPolicy;

    @Transactional(readOnly = true)
    public MatchingStatusQueryResult getStatus(
            Long memberId,
            Long matchingRequestId
    ) {
        MatchingRequest matchingRequest = matchingRequestRepository.findById(matchingRequestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND));

        validateOwner(memberId, matchingRequest);

        // 조회 API는 상태를 바꾸지 않고 현재 요청 주변 row를 모아 Android 복구 응답을 만든다.
        Optional<MatchingRequestGroupItem> matchingRequestGroupItem =
                matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(matchingRequestId);
        Optional<MatchingRequestGroup> matchingRequestGroup = matchingRequestGroupItem
                .map(MatchingRequestGroupItem::getMatchingRequestGroup);
        Optional<MatchingOffer> matchingOffer = findCurrentOffer(matchingRequest, matchingRequestGroup);
        Optional<MatchingRequestPayment> matchingRequestPayment = matchingOffer
                .map(MatchingOffer::getId)
                .flatMap(matchingOfferId -> matchingRequestPaymentRepository
                        .findByMatchingRequestIdAndMatchingOfferId(matchingRequestId, matchingOfferId));

        MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                matchingRequest,
                matchingRequestGroup,
                matchingRequestGroupItem,
                matchingOffer,
                matchingRequestPayment
        );
        Optional<Lesson> lesson = findConfirmedLesson(matchingStatus, matchingOffer);

        return toQueryResult(
                matchingRequest,
                matchingStatus,
                matchingRequestGroup,
                matchingRequestGroupItem,
                matchingOffer,
                matchingRequestPayment,
                lesson
        );
    }

    private MatchingStatusQueryResult toQueryResult(
            MatchingRequest matchingRequest,
            MatchingStatus matchingStatus,
            Optional<MatchingRequestGroup> matchingRequestGroup,
            Optional<MatchingRequestGroupItem> matchingRequestGroupItem,
            Optional<MatchingOffer> matchingOffer,
            Optional<MatchingRequestPayment> matchingRequestPayment,
            Optional<Lesson> lesson
    ) {
        Optional<MatchingRequestGroup> responseMatchingRequestGroup = matchingRequestGroup
                .or(() -> matchingOffer.map(MatchingOffer::getMatchingRequestGroup));

        return new MatchingStatusQueryResult(
                matchingRequest.getId(),
                matchingStatus,
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                responseMatchingRequestGroup.map(MatchingRequestGroup::getId).orElse(null),
                responseMatchingRequestGroup.map(MatchingRequestGroup::getStatus).orElse(null),
                matchingRequestGroupItem.map(MatchingRequestGroupItem::getStatus).orElse(null),
                matchingOffer.map(MatchingOffer::getStatus).orElse(null),
                matchingRequestPayment.map(MatchingRequestPayment::getStatus).orElse(null),
                matchingTimeoutPolicy.matchingStatusExpiresAt(
                        matchingStatus,
                        matchingRequest,
                        matchingOffer,
                        matchingRequestPayment
                ).orElse(null),
                resolveInstructorProfile(matchingOffer),
                resolveLessonId(matchingStatus, lesson),
                resolvePriceSummary(matchingStatus, matchingOffer, matchingRequestPayment)
        );
    }

    // 최종 확인 단계는 제안 가격, 결제 이후 단계는 요청 가격을 사용해 저장 시점 경계 유지
    private MatchingPriceSummaryResult resolvePriceSummary(
            MatchingStatus matchingStatus,
            Optional<MatchingOffer> matchingOffer,
            Optional<MatchingRequestPayment> matchingRequestPayment
    ) {
        return switch (matchingStatus) {
            case WAITING_FOR_CONFIRMATION, WAITING_FOR_OTHER_CONFIRMATIONS ->
                    resolveOfferPriceSummary(matchingOffer);
            case PAYMENT_PENDING, WAITING_FOR_OTHER_PAYMENTS, CONFIRMED ->
                    resolveRequestPriceSummary(matchingRequestPayment);
            default -> null;
        };
    }

    private MatchingPriceSummaryResult resolveOfferPriceSummary(Optional<MatchingOffer> matchingOffer) {
        Long matchingOfferId = matchingOffer
                .map(MatchingOffer::getId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
        return matchingOfferPriceSnapshotRepository.findByMatchingOfferId(matchingOfferId)
                .map(MatchingPriceSummaryResult::from)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
    }

    private MatchingPriceSummaryResult resolveRequestPriceSummary(
            Optional<MatchingRequestPayment> matchingRequestPayment
    ) {
        return matchingRequestPayment
                .map(MatchingRequestPayment::getMatchingRequestPriceSnapshot)
                .map(MatchingPriceSummaryResult::from)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
    }

    private static MatchingStatusQueryResult.InstructorProfileResult resolveInstructorProfile(
            Optional<MatchingOffer> matchingOffer
    ) {
        return matchingOffer
                .filter(offer -> offer.getStatus() == MatchingOfferStatus.ACCEPTED)
                .map(MatchingOffer::getInstructorProfile)
                .map(MatchingStatusQueryResult.InstructorProfileResult::from)
                .orElse(null);
    }

    private static Long resolveLessonId(
            MatchingStatus matchingStatus,
            Optional<Lesson> lesson
    ) {
        // 확정 매칭 화면 복구에만 필요한 강습 ID 노출
        if (matchingStatus != MatchingStatus.CONFIRMED) {
            return null;
        }

        return lesson.map(Lesson::getId).orElse(null);
    }

    private void validateOwner(
            Long memberId,
            MatchingRequest matchingRequest
    ) {
        Long ownerId = matchingRequest.getMember().getId();
        // 컨트롤러 권한 통과 이후에도 요청 소유자만 조회 가능한 도메인 보안 경계
        if (!Objects.equals(ownerId, memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    // 재매칭 뒤 요청에 남은 과거 제안보다 최신 그룹과 연결된 제안을 우선해 상태 오염 방지
    private Optional<MatchingOffer> findCurrentOffer(
            MatchingRequest matchingRequest,
            Optional<MatchingRequestGroup> matchingRequestGroup
    ) {
        MatchingOffer requestOffer = matchingRequest.getMatchingOffer();
        if (matchingRequestGroup.isPresent()) {
            Long currentGroupId = matchingRequestGroup.get().getId();
            if (requestOffer != null
                    && Objects.equals(requestOffer.getMatchingRequestGroup().getId(), currentGroupId)) {
                return Optional.of(requestOffer);
            }

            return matchingOfferRepository.findFirstByMatchingRequestGroupIdOrderByIdDesc(currentGroupId);
        }

        return Optional.ofNullable(requestOffer);
    }

    private Optional<Lesson> findConfirmedLesson(
            MatchingStatus matchingStatus,
            Optional<MatchingOffer> matchingOffer
    ) {
        // 확정 상태에서만 강습 ID를 노출하는 API 계약 보호
        if (matchingStatus != MatchingStatus.CONFIRMED) {
            return Optional.empty();
        }

        return matchingOffer
                .map(MatchingOffer::getId)
                .filter(Objects::nonNull)
                .flatMap(lessonRepository::findByMatchingOfferId);
    }
}
