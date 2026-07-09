package org.sopt.ssingserver.domain.matching.realtime;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.event.InstructorAcceptedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCanceledEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentPendingEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.RequesterConfirmationUpdatedEvent;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchingNotificationContextLoader {

    private static final String IMMEDIATE_START_TYPE = "IMMEDIATE";

    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingGroupNotificationContext> load(MatchingOfferCreatedEvent event) {
        return loadGroup(event.matchingRequestGroupId(), event.matchingOfferId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingGroupNotificationContext> load(InstructorAcceptedEvent event) {
        return loadGroup(event.matchingRequestGroupId(), event.matchingOfferId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingGroupNotificationContext> load(RequesterConfirmationUpdatedEvent event) {
        return loadGroup(event.matchingRequestGroupId(), event.matchingOfferId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingPaymentNotificationContext> load(PaymentPendingEvent event) {
        return loadPaymentGroup(event.matchingRequestGroupId(), event.matchingOfferId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingPaymentNotificationContext> load(PaymentStatusChangedEvent event) {
        return loadPaymentGroup(event.matchingRequestGroupId(), event.matchingOfferId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingGroupNotificationContext> load(MatchingConfirmedEvent event) {
        return loadGroup(event.matchingRequestGroupId(), event.matchingOfferId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingOfferRecipientContext> load(MatchingOfferClosedEvent event) {
        return loadOfferRecipient(event.matchingOfferId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingOfferRecipientContext> load(MatchingOfferCanceledEvent event) {
        return loadOfferRecipient(event.matchingOfferId());
    }

    // 상태 변경 이벤트 수신자인 소비자 memberId를 DB에서 다시 확인한다.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MatchingStatusChangedContext> load(MatchingRequestStatusChangedEvent event) {
        return matchingRequestRepository.findRealtimeStatusContextById(event.matchingRequestId())
                .map(matchingRequest -> new MatchingStatusChangedContext(matchingRequest.getMember().getId()));
    }

    private Optional<MatchingPaymentNotificationContext> loadPaymentGroup(
            Long groupId,
            Long offerId
    ) {
        Optional<MatchingGroupNotificationContext> groupContext = loadGroup(groupId, offerId);
        if (groupContext.isEmpty()) {
            return Optional.empty();
        }

        List<MatchingRequestPayment> payments = matchingRequestPaymentRepository
                .findRealtimeContextByMatchingOfferIdOrderByIdAsc(offerId);
        if (payments.isEmpty()) {
            return Optional.empty();
        }

        List<PaymentRecipientContext> recipients = payments.stream()
                .map(payment -> new PaymentRecipientContext(
                        payment.getMatchingRequest().getMember().getId(),
                        payment.getMatchingRequest().getId(),
                        payment.getId(),
                        payment.getStatus()
                ))
                .toList();
        return Optional.of(new MatchingPaymentNotificationContext(groupContext.get(), recipients));
    }

    // 커밋 이후 이벤트 payload에 필요한 강사·소비자·강습 요약을 한 번에 재구성한다.
    private Optional<MatchingGroupNotificationContext> loadGroup(
            Long groupId,
            Long offerId
    ) {
        Optional<MatchingOffer> matchingOffer = matchingOfferRepository.findRealtimeContextById(offerId);
        if (matchingOffer.isEmpty()) {
            return Optional.empty();
        }

        List<MatchingRequestGroupItem> groupItems = matchingRequestGroupItemRepository
                .findRealtimeContextByMatchingRequestGroupIdOrderByIdAsc(groupId);
        if (groupItems.isEmpty()) {
            return Optional.empty();
        }

        MatchingOffer offer = matchingOffer.get();
        MatchingRequest firstRequest = groupItems.getFirst().getMatchingRequest();
        InstructorProfile instructorProfile = offer.getInstructorProfile();
        int totalHeadcount = groupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .mapToInt(MatchingRequest::getHeadcount)
                .sum();

        InstructorNotificationContext instructor = new InstructorNotificationContext(
                instructorProfile.getMember().getId(),
                instructorProfile.getId(),
                instructorProfile.getRealName(),
                instructorProfile.getMember().getProfileImageUrl()
        );
        List<ConsumerNotificationContext> consumers = groupItems.stream()
                .map(item -> new ConsumerNotificationContext(
                        item.getMatchingRequest().getMember().getId(),
                        item.getMatchingRequest().getId(),
                        item.getStatus()
                ))
                .toList();

        return Optional.of(new MatchingGroupNotificationContext(
                instructor,
                consumers,
                firstRequest.getMember().getNickname(),
                firstRequest.getHeadcount(),
                groupItems.size(),
                firstRequest.getResort().getDisplayName(),
                firstRequest.getSport().name(),
                firstRequest.getLessonLevel().name(),
                offer.getMatchingRequestGroup().getDurationMinutes(),
                totalHeadcount,
                IMMEDIATE_START_TYPE
        ));
    }

    private Optional<MatchingOfferRecipientContext> loadOfferRecipient(Long offerId) {
        return matchingOfferRepository.findRealtimeContextById(offerId)
                .map(offer -> new MatchingOfferRecipientContext(
                        offer.getInstructorProfile().getMember().getId()
                ));
    }

    public record MatchingGroupNotificationContext(
            InstructorNotificationContext instructor,
            List<ConsumerNotificationContext> consumers,
            String requesterName,
            int headcount,
            int matchingRequestCount,
            String resortName,
            String sport,
            String level,
            int durationMinutes,
            int totalHeadcount,
            String startType
    ) {
    }

    public record InstructorNotificationContext(
            Long recipientMemberId,
            Long instructorProfileId,
            String name,
            String profileImageUrl
    ) {
    }

    public record ConsumerNotificationContext(
            Long recipientMemberId,
            Long matchingRequestId,
            MatchingRequestGroupItemStatus confirmationStatus
    ) {
    }

    public record MatchingPaymentNotificationContext(
            MatchingGroupNotificationContext group,
            List<PaymentRecipientContext> recipients
    ) {
    }

    public record PaymentRecipientContext(
            Long recipientMemberId,
            Long matchingRequestId,
            Long matchingRequestPaymentId,
            MatchingRequestPaymentStatus paymentStatus
    ) {
    }

    public record MatchingOfferRecipientContext(Long recipientMemberId) {
    }

    public record MatchingStatusChangedContext(Long recipientMemberId) {
    }
}
