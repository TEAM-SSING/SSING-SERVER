package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingConfirmationResult;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingPaymentResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.matching.enums.MatchingConfirmationDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPriceSnapshot;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPriceSnapshotRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsumerMatchingProgressService {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupRepository matchingRequestGroupRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;
    private final MatchingRequestPriceSnapshotRepository matchingRequestPriceSnapshotRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final MatchingRequestParticipantRepository matchingRequestParticipantRepository;
    private final LessonRepository lessonRepository;
    private final LessonParticipantRepository lessonParticipantRepository;
    private final Clock clock;

    // 강사 수락 이후 대표 소비자의 최종 수락/거절을 단일 트랜잭션에서 반영
    @Transactional
    public ConsumerMatchingConfirmationResult respond(
            Long memberId,
            Long matchingRequestId,
            MatchingConfirmationDecision decision
    ) {
        MatchingRequest matchingRequest = findRequestForUpdate(matchingRequestId);
        validateOwner(memberId, matchingRequest);
        MatchingProgressContext context = findConfirmationContext(matchingRequest);
        validateConfirmable(context.matchingRequest(), context.group(), context.currentItem());

        Instant now = clock.instant();
        return switch (decision) {
            case ACCEPTED -> accept(context, now);
            case REJECTED -> reject(context, now);
        };
    }

    // 결제 버튼 클릭을 현재 요청자의 PENDING 결제 완료로 반영하고, 마지막 결제면 강습 생성까지 진행
    @Transactional
    public ConsumerMatchingPaymentResult completePayment(
            Long memberId,
            Long matchingRequestId
    ) {
        MatchingRequest matchingRequest = findRequestForUpdate(matchingRequestId);
        validateOwner(memberId, matchingRequest);
        MatchingProgressContext context = findPaymentContext(matchingRequest);
        validatePaymentCompletable(context.matchingRequest(), context.group(), context.currentItem());

        MatchingRequestPayment payment = matchingRequestPaymentRepository
                .findFirstByMatchingRequestIdAndStatusOrderByIdDesc(
                        matchingRequest.getId(),
                        MatchingRequestPaymentStatus.PENDING
                )
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_PAYMENT_NOT_PENDING));

        Instant now = clock.instant();
        payment.complete(now);

        MatchingOffer matchingOffer = requireAcceptedOffer(matchingRequest);
        List<MatchingRequestPayment> payments = matchingRequestPaymentRepository.findByMatchingOfferIdForUpdate(
                matchingOffer.getId()
        );
        if (payments.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        int paidCount = countCompletedPayments(payments);
        int requiredCount = payments.size();
        if (paidCount != requiredCount) {
            return new ConsumerMatchingPaymentResult(
                    matchingRequest.getId(),
                    MatchingStatus.WAITING_FOR_OTHER_PAYMENTS,
                    payment.getStatus(),
                    context.group().getId(),
                    context.group().getStatus(),
                    paidCount,
                    requiredCount,
                    null,
                    null
            );
        }

        context.group().confirm();
        for (MatchingRequestGroupItem groupItem : context.groupItems()) {
            groupItem.getMatchingRequest().confirm();
        }
        Lesson lesson = createConfirmedLesson(context, matchingOffer, now);

        return new ConsumerMatchingPaymentResult(
                matchingRequest.getId(),
                MatchingStatus.CONFIRMED,
                payment.getStatus(),
                context.group().getId(),
                context.group().getStatus(),
                paidCount,
                requiredCount,
                lesson.getId(),
                null
        );
    }

    // 모든 대표 소비자가 수락한 경우에만 요청별 가격 스냅샷과 결제 대기 row 생성
    private ConsumerMatchingConfirmationResult accept(
            MatchingProgressContext context,
            Instant now
    ) {
        context.currentItem().accept(now);
        int confirmedCount = countAcceptedItems(context.groupItems());
        int requiredCount = context.groupItems().size();
        if (confirmedCount != requiredCount) {
            return new ConsumerMatchingConfirmationResult(
                    context.matchingRequest().getId(),
                    MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS,
                    context.currentItem().getStatus(),
                    context.matchingRequest().getStatus(),
                    context.matchingRequest().getStatusReason(),
                    context.group().getId(),
                    context.group().getStatus(),
                    context.currentItem().getStatus(),
                    confirmedCount,
                    requiredCount,
                    null
            );
        }

        context.group().markConsumerAccepted();
        createPendingPayments(context, now);
        context.group().markPaymentPending();

        return new ConsumerMatchingConfirmationResult(
                context.matchingRequest().getId(),
                MatchingStatus.PAYMENT_PENDING,
                context.currentItem().getStatus(),
                context.matchingRequest().getStatus(),
                context.matchingRequest().getStatusReason(),
                context.group().getId(),
                context.group().getStatus(),
                context.currentItem().getStatus(),
                null,
                null,
                null
        );
    }

    // 소비자 거절은 요청 종료가 아니라 같은 요청을 유지한 재탐색 상태로 전환
    private ConsumerMatchingConfirmationResult reject(
            MatchingProgressContext context,
            Instant now
    ) {
        MatchingOffer matchingOffer = requireAcceptedOffer(context.matchingRequest());
        matchingOffer.cancel();
        context.group().cancel();
        for (MatchingRequestGroupItem groupItem : context.groupItems()) {
            if (Objects.equals(groupItem.getId(), context.currentItem().getId())) {
                groupItem.reject(now);
            } else {
                groupItem.cancel();
            }
            groupItem.getMatchingRequest().rematchAfterConsumerRejected();
        }

        return new ConsumerMatchingConfirmationResult(
                context.matchingRequest().getId(),
                MatchingStatus.REMATCHING,
                context.currentItem().getStatus(),
                context.matchingRequest().getStatus(),
                context.matchingRequest().getStatusReason(),
                context.group().getId(),
                context.group().getStatus(),
                context.currentItem().getStatus(),
                null,
                null,
                null
        );
    }

    private void createPendingPayments(
            MatchingProgressContext context,
            Instant now
    ) {
        if (context.groupItems().size() != 1) {
            // 다중 요청 그룹의 분담금/반올림 정책은 MVP 이후 확정한다.
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        MatchingOffer matchingOffer = requireAcceptedOffer(context.matchingRequest());
        MatchingOfferPriceSnapshot offerPriceSnapshot = matchingOfferPriceSnapshotRepository
                .findByMatchingOfferId(matchingOffer.getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
        int consumerPaymentAmount = offerPriceSnapshot.getConsumerTotalAmount();

        for (MatchingRequestGroupItem groupItem : context.groupItems()) {
            MatchingRequest groupRequest = groupItem.getMatchingRequest();
            MatchingRequestPriceSnapshot requestPriceSnapshot = matchingRequestPriceSnapshotRepository.save(
                    MatchingRequestPriceSnapshot.create(
                            groupRequest,
                            offerPriceSnapshot,
                            consumerPaymentAmount
                    )
            );
            matchingRequestPaymentRepository.save(MatchingRequestPayment.createPending(
                    groupRequest,
                    requestPriceSnapshot,
                    matchingOffer,
                    requestPriceSnapshot.getConsumerPaymentAmount(),
                    now,
                    null
            ));
        }
    }

    // 결제 전체 완료 시 매칭 그룹 정보를 강습과 강습 참가자 row로 확정 복사
    private Lesson createConfirmedLesson(
            MatchingProgressContext context,
            MatchingOffer matchingOffer,
            Instant confirmedAt
    ) {
        MatchingRequest firstRequest = context.groupItems().getFirst().getMatchingRequest();
        int totalHeadcount = context.groupItems().stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .mapToInt(MatchingRequest::getHeadcount)
                .sum();
        Lesson lesson = lessonRepository.save(Lesson.createImmediateConfirmed(
                matchingOffer.getInstructorProfile(),
                firstRequest.getResort(),
                matchingOffer,
                firstRequest.getSport(),
                firstRequest.getLessonLevel(),
                totalHeadcount,
                context.group().getDurationMinutes(),
                confirmedAt
        ));

        List<Long> matchingRequestIds = context.groupItems().stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .map(MatchingRequest::getId)
                .toList();
        List<MatchingRequestParticipant> matchingRequestParticipants = matchingRequestParticipantRepository
                .findByMatchingRequestIdInOrderByMatchingRequestIdAscIdAsc(matchingRequestIds);
        List<LessonParticipant> lessonParticipants = matchingRequestParticipants.stream()
                .map(participant -> LessonParticipant.create(
                        lesson,
                        participant.getMatchingRequest(),
                        participant
                ))
                .toList();
        lessonParticipantRepository.saveAll(lessonParticipants);
        return lesson;
    }

    private MatchingRequest findRequestForUpdate(Long matchingRequestId) {
        return matchingRequestRepository.findByIdForUpdate(matchingRequestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND));
    }

    private MatchingProgressContext findConfirmationContext(MatchingRequest matchingRequest) {
        MatchingRequestGroupItem latestItem = matchingRequestGroupItemRepository
                .findFirstByMatchingRequestIdOrderByIdDesc(matchingRequest.getId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_CONFIRMABLE));
        MatchingRequestGroup group = findGroupForUpdate(
                latestItem.getMatchingRequestGroup().getId(),
                MatchingErrorCode.MATCHING_REQUEST_NOT_CONFIRMABLE
        );
        List<MatchingRequestGroupItem> groupItems = findGroupItemsForUpdate(group);
        MatchingRequestGroupItem currentItem = findCurrentItem(groupItems, matchingRequest.getId());
        return new MatchingProgressContext(matchingRequest, group, currentItem, groupItems);
    }

    private MatchingProgressContext findPaymentContext(MatchingRequest matchingRequest) {
        MatchingRequestGroupItem latestItem = matchingRequestGroupItemRepository
                .findFirstByMatchingRequestIdOrderByIdDesc(matchingRequest.getId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_PAYMENT_NOT_PENDING));
        MatchingRequestGroup group = findGroupForUpdate(
                latestItem.getMatchingRequestGroup().getId(),
                MatchingErrorCode.MATCHING_PAYMENT_NOT_PENDING
        );
        List<MatchingRequestGroupItem> groupItems = findGroupItemsForUpdate(group);
        MatchingRequestGroupItem currentItem = findCurrentItem(groupItems, matchingRequest.getId());
        return new MatchingProgressContext(matchingRequest, group, currentItem, groupItems);
    }

    private MatchingRequestGroup findGroupForUpdate(
            Long groupId,
            MatchingErrorCode notFoundErrorCode
    ) {
        return matchingRequestGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(notFoundErrorCode));
    }

    private List<MatchingRequestGroupItem> findGroupItemsForUpdate(MatchingRequestGroup group) {
        List<MatchingRequestGroupItem> groupItems = matchingRequestGroupItemRepository
                .findByMatchingRequestGroupIdForUpdate(group.getId());
        if (groupItems.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return groupItems;
    }

    private MatchingRequestGroupItem findCurrentItem(
            List<MatchingRequestGroupItem> groupItems,
            Long matchingRequestId
    ) {
        return groupItems.stream()
                .filter(item -> Objects.equals(item.getMatchingRequest().getId(), matchingRequestId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
    }

    private void validateConfirmable(
            MatchingRequest matchingRequest,
            MatchingRequestGroup group,
            MatchingRequestGroupItem currentItem
    ) {
        if (matchingRequest.getStatus() != MatchingRequestStatus.MATCHED
                || group.getStatus() != MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED
                || currentItem.getStatus() != MatchingRequestGroupItemStatus.PENDING
                || !isAcceptedOffer(matchingRequest.getMatchingOffer())) {
            throw new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_CONFIRMABLE);
        }
    }

    private void validatePaymentCompletable(
            MatchingRequest matchingRequest,
            MatchingRequestGroup group,
            MatchingRequestGroupItem currentItem
    ) {
        if (matchingRequest.getStatus() != MatchingRequestStatus.MATCHED
                || group.getStatus() != MatchingRequestGroupStatus.PAYMENT_PENDING
                || currentItem.getStatus() != MatchingRequestGroupItemStatus.ACCEPTED
                || !isAcceptedOffer(matchingRequest.getMatchingOffer())) {
            throw new BusinessException(MatchingErrorCode.MATCHING_PAYMENT_NOT_PENDING);
        }
    }

    private MatchingOffer requireAcceptedOffer(MatchingRequest matchingRequest) {
        MatchingOffer matchingOffer = matchingRequest.getMatchingOffer();
        if (!isAcceptedOffer(matchingOffer)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return matchingOffer;
    }

    private boolean isAcceptedOffer(MatchingOffer matchingOffer) {
        return matchingOffer != null && matchingOffer.getStatus() == MatchingOfferStatus.ACCEPTED;
    }

    private int countAcceptedItems(List<MatchingRequestGroupItem> groupItems) {
        return (int) groupItems.stream()
                .filter(item -> item.getStatus() == MatchingRequestGroupItemStatus.ACCEPTED)
                .count();
    }

    private int countCompletedPayments(List<MatchingRequestPayment> payments) {
        return (int) payments.stream()
                .filter(payment -> payment.getStatus() == MatchingRequestPaymentStatus.COMPLETED)
                .count();
    }

    private void validateOwner(
            Long memberId,
            MatchingRequest matchingRequest
    ) {
        Long ownerId = matchingRequest.getMember().getId();
        if (!Objects.equals(ownerId, memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private record MatchingProgressContext(
            MatchingRequest matchingRequest,
            MatchingRequestGroup group,
            MatchingRequestGroupItem currentItem,
            List<MatchingRequestGroupItem> groupItems
    ) {
    }
}
