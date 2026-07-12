package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCancellationResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCanceledEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 소비자 매칭 중지 요청의 소유자 검증, 상태 전환, 주변 row 정리를 담당하는 쓰기 서비스
@Service
@RequiredArgsConstructor
public class MatchingCancellationService {

    private static final Logger log = LoggerFactory.getLogger(MatchingCancellationService.class);
    private static final List<MatchingRequestStatus> CANCELLABLE_REQUEST_STATUSES = List.of(
            MatchingRequestStatus.REQUESTED,
            MatchingRequestStatus.GROUPED,
            MatchingRequestStatus.MATCHED
    );
    private static final List<MatchingOfferStatus> ACTIVE_OFFER_STATUSES = List.of(
            MatchingOfferStatus.OFFERED,
            MatchingOfferStatus.ACCEPTED
    );
    private static final List<MatchingRequestPaymentStatus> PAYMENT_COMPLETION_FLOW_STATUSES = List.of(
            MatchingRequestPaymentStatus.COMPLETED,
            MatchingRequestPaymentStatus.REFUND_REQUIRED,
            MatchingRequestPaymentStatus.REFUNDED
    );

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final MatchingStatusResolver matchingStatusResolver;
    private final MatchingEventDispatcher matchingEventDispatcher;
    private final MatchingAfterCommitExecutor matchingAfterCommitExecutor;
    private final Clock clock;

    // Controller에서 들어온 소비자 중지 요청의 단일 트랜잭션 경계
    @Transactional
    public MatchingCancellationResult cancel(
            Long memberId,
            Long matchingRequestId
    ) {
        MatchingRequest matchingRequest = matchingRequestRepository.findByIdForUpdate(matchingRequestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND));
        validateOwner(memberId, matchingRequest);
        validateCancellableRequestStatus(matchingRequest);

        Optional<MatchingRequestGroupItem> matchingRequestGroupItem =
                matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(matchingRequestId);
        Optional<MatchingRequestGroup> matchingRequestGroup = matchingRequestGroupItem
                .map(MatchingRequestGroupItem::getMatchingRequestGroup);
        Optional<MatchingRequestPayment> matchingRequestPayment =
                matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(matchingRequestId);

        validatePaymentCancellable(matchingRequestPayment);

        List<MatchingOffer> activeOffers = findActiveOffers(matchingRequestGroup);
        Instant now = clock.instant();

        matchingRequest.cancelByConsumer(now);
        // TODO: 복합팀 매칭 도입 시 한 요청 취소가 같은 그룹의 다른 요청/결제에 미치는 정책을 함께 반영한다.
        matchingRequestGroup.ifPresent(MatchingRequestGroup::cancel);
        matchingRequestGroupItem.ifPresent(MatchingRequestGroupItem::cancel);
        activeOffers.forEach(MatchingOffer::cancel);
        cancelPendingPayment(matchingRequestPayment, now);

        Optional<MatchingOffer> currentOffer = activeOffers.stream().findFirst()
                .or(() -> Optional.ofNullable(matchingRequest.getMatchingOffer()));
        MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                matchingRequest,
                matchingRequestGroup,
                matchingRequestGroupItem,
                currentOffer,
                matchingRequestPayment
        );

        matchingEventDispatcher.publishAfterCommit(new MatchingRequestStatusChangedEvent(
                UUID.randomUUID(),
                now,
                matchingRequest.getId(),
                matchingRequestGroup.map(MatchingRequestGroup::getId).orElse(null),
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                matchingStatus
        ));
        activeOffers.forEach(offer -> matchingEventDispatcher.publishAfterCommit(new MatchingOfferCanceledEvent(
                UUID.randomUUID(),
                now,
                offer.getMatchingRequestGroup().getId(),
                offer.getId(),
                matchingRequest.getStatusReason()
        )));

        boolean paymentCanceled = matchingRequestPayment
                .map(payment -> payment.getStatus() == MatchingRequestPaymentStatus.CANCELED)
                .orElse(false);
        Long canceledRequestId = matchingRequest.getId();
        String canceledMatchingStatus = matchingStatus.name();
        String canceledRequestStatus = matchingRequest.getStatus().name();
        int canceledOfferCount = activeOffers.size();
        // rollback 시 거짓 성공 로그가 남지 않도록 안전한 값만 캡처해 커밋 이후로 넘긴다.
        matchingAfterCommitExecutor.execute(
                "matching-cancellation-success-log",
                () -> logCancellationSuccess(
                        memberId,
                        canceledRequestId,
                        canceledMatchingStatus,
                        canceledRequestStatus,
                        canceledOfferCount,
                        paymentCanceled
                )
        );

        return MatchingCancellationResult.of(matchingRequest, matchingStatus);
    }

    private void validateOwner(
            Long memberId,
            MatchingRequest matchingRequest
    ) {
        Long ownerId = matchingRequest.getMember().getId();
        // 컨트롤러 권한 통과 이후에도 요청 소유자만 취소 가능한 도메인 보안 경계
        if (!Objects.equals(ownerId, memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private void validateCancellableRequestStatus(MatchingRequest matchingRequest) {
        if (!CANCELLABLE_REQUEST_STATUSES.contains(matchingRequest.getStatus())) {
            throw new BusinessException(MatchingErrorCode.MATCHING_CANCEL_NOT_ALLOWED);
        }
    }

    private void validatePaymentCancellable(Optional<MatchingRequestPayment> matchingRequestPayment) {
        matchingRequestPayment
                .map(MatchingRequestPayment::getStatus)
                .filter(PAYMENT_COMPLETION_FLOW_STATUSES::contains)
                .ifPresent(status -> {
                    // MVP에서는 결제 완료 이후 환불/롤백 정책을 다루지 않으므로 매칭 중지 API에서 차단한다.
                    throw new BusinessException(MatchingErrorCode.MATCHING_CANCEL_NOT_ALLOWED);
                });
    }

    private List<MatchingOffer> findActiveOffers(Optional<MatchingRequestGroup> matchingRequestGroup) {
        return matchingRequestGroup
                .map(MatchingRequestGroup::getId)
                .filter(Objects::nonNull)
                .map(groupId -> matchingOfferRepository.findByMatchingRequestGroupIdAndStatusIn(
                        groupId,
                        ACTIVE_OFFER_STATUSES
                ))
                .orElseGet(List::of);
    }

    private void cancelPendingPayment(
            Optional<MatchingRequestPayment> matchingRequestPayment,
            Instant canceledAt
    ) {
        matchingRequestPayment
                .filter(payment -> payment.getStatus() == MatchingRequestPaymentStatus.PENDING)
                .ifPresent(payment -> payment.cancel(canceledAt));
    }

    private void logCancellationSuccess(
            Long memberId,
            Long matchingRequestId,
            String matchingStatus,
            String requestStatus,
            int canceledOfferCount,
            boolean paymentCanceled
    ) {
        log.atInfo()
                .addKeyValue("event", "matching.request.cancel.success")
                .addKeyValue("matching_request_id", String.valueOf(matchingRequestId))
                .addKeyValue("member_id", String.valueOf(memberId))
                .addKeyValue("matching_status", matchingStatus)
                .addKeyValue("request_status", requestStatus)
                .addKeyValue("canceled_offer_count", canceledOfferCount)
                .addKeyValue("payment_canceled", paymentCanceled)
                .log("Matching request canceled");
    }

}
