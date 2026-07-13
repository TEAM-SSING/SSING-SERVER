package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.payment.entity.MatchingOfferPriceSnapshot;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPriceSnapshot;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchingStatusQueryServiceTest {

    private static final Instant PAYMENT_EXPIRES_AT = Instant.parse("2026-07-07T00:15:00Z");
    private static final Instant OFFER_EXPOSED_AT = Instant.parse("2026-07-07T00:00:00Z");
    private static final Instant OFFER_RESPONDED_AT = Instant.parse("2026-07-07T00:01:00Z");

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;

    @Mock
    private MatchingRequestPaymentRepository matchingRequestPaymentRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private MatchingStatusResolver matchingStatusResolver;

    @Test
    void getActiveStatus는_활성_요청이_없으면_empty를_반환한다() {
        MatchingStatusQueryService service = createService();
        when(matchingRequestRepository.findByMemberIdAndStatusIn(
                1L,
                MatchingRequestStatus.activeNegotiationStatuses()
        )).thenReturn(Optional.empty());

        assertThat(service.getActiveStatus(1L)).isEmpty();

        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
        verifyNoInteractions(lessonRepository);
        verifyNoInteractions(matchingStatusResolver);
    }

    @Test
    void getActiveStatus는_본인의_활성_요청을_기존_상태_응답으로_복구한다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest matchingRequest = matchingRequest(10L, member(1L, "요청자", MemberRole.CONSUMER));
        when(matchingRequestRepository.findByMemberIdAndStatusIn(
                1L,
                MatchingRequestStatus.activeNegotiationStatuses()
        )).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.empty());
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        )).thenReturn(MatchingStatus.SEARCHING);

        MatchingStatusQueryResult result = service.getActiveStatus(1L).orElseThrow();

        assertThat(result.matchingRequestId()).isEqualTo(10L);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.SEARCHING);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
    }

    @Test
    void getStatus는_없는_요청이면_MATCHING_REQUEST_NOT_FOUND를_던진다() {
        MatchingStatusQueryService service = createService();
        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isSameAs(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND);

        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
        verifyNoInteractions(lessonRepository);
        verifyNoInteractions(matchingStatusResolver);
    }

    @Test
    void getStatus는_다른_소비자의_요청이면_FORBIDDEN을_던진다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest matchingRequest = matchingRequest(10L, member(1L, "요청자", MemberRole.CONSUMER));
        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));

        assertThatThrownBy(() -> service.getStatus(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isSameAs(CommonErrorCode.FORBIDDEN);

        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingRequestPaymentRepository);
        verifyNoInteractions(lessonRepository);
        verifyNoInteractions(matchingStatusResolver);
    }

    @Test
    void getStatus는_SEARCHING_요청이면_필수_상태만_반환한다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest matchingRequest = matchingRequest(10L, member(1L, "요청자", MemberRole.CONSUMER));
        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.empty());
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        )).thenReturn(MatchingStatus.SEARCHING);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.matchingRequestId()).isEqualTo(10L);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.SEARCHING);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(result.requestStatusReason()).isNull();
        assertThat(result.groupId()).isNull();
        assertThat(result.groupStatus()).isNull();
        assertThat(result.itemStatus()).isNull();
        assertThat(result.offerStatus()).isNull();
        assertThat(result.paymentStatus()).isNull();
        assertThat(result.instructorProfile()).isNull();
        assertThat(result.lessonId()).isNull();
        assertThat(result.priceSummary()).isNull();
        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingOfferPriceSnapshotRepository);
        verifyNoInteractions(lessonRepository);
    }

    @Test
    void getStatus는_강사_응답대기_요청이면_그룹의_최신_제안을_조회해_반환한다() {
        MatchingStatusQueryService service = createService();
        Member consumer = member(1L, "요청자", MemberRole.CONSUMER);
        MatchingRequest matchingRequest = matchingRequest(10L, consumer);
        MatchingRequestGroup group = exposedMatchingRequestGroup(20L);
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(matchingRequest, group);
        ReflectionTestUtils.setField(item, "id", 30L);
        MatchingOffer offer = offeredOffer(50L, instructorProfile(40L), group);
        matchingRequest.markGrouped();

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingOfferRepository.findFirstByMatchingRequestGroupIdOrderByIdDesc(20L))
                .thenReturn(Optional.of(offer));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.empty()
        )).thenReturn(MatchingStatus.WAITING_FOR_INSTRUCTOR);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);
        assertThat(result.groupId()).isEqualTo(20L);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(result.itemStatus()).isSameAs(MatchingRequestGroupItemStatus.NOT_REQUESTED);
        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(result.expiresAt()).isNull();
        assertThat(result.instructorProfile()).isNull();
        assertThat(result.priceSummary()).isNull();
        verifyNoInteractions(matchingOfferPriceSnapshotRepository);
        verifyNoInteractions(lessonRepository);
    }

    @Test
    void getStatus는_강사_수락_이후_그룹_아이템_제안_강사프로필을_반환한다() {
        MatchingStatusQueryService service = createService();
        Member consumer = member(1L, "요청자", MemberRole.CONSUMER);
        MatchingRequest matchingRequest = matchingRequest(10L, consumer);
        MatchingRequestGroup group = matchingRequestGroup(20L);
        MatchingRequestGroupItem item = matchingRequestGroupItem(30L, matchingRequest, group);
        InstructorProfile instructorProfile = instructorProfile(40L);
        ReflectionTestUtils.setField(instructorProfile, "level", 3);
        MatchingOffer offer = acceptedOffer(50L, instructorProfile, group);
        matchingRequest.markMatched(offer);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdOrderByIdAsc(20L))
                .thenReturn(List.of(item));
        MatchingOfferPriceSnapshot offerPriceSnapshot = offerPriceSnapshot(offer, 80_000, 20_000);
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L))
                .thenReturn(Optional.of(offerPriceSnapshot));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.empty()
        )).thenReturn(MatchingStatus.WAITING_FOR_CONFIRMATION);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_CONFIRMATION);
        assertThat(result.groupId()).isEqualTo(20L);
        assertThat(result.groupStatus()).isSameAs(MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED);
        assertThat(result.itemStatus()).isSameAs(MatchingRequestGroupItemStatus.PENDING);
        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.ACCEPTED);
        assertThat(result.expiresAt()).isNull();
        assertThat(result.instructorProfile()).isNotNull();
        assertThat(result.instructorProfile().instructorId()).isEqualTo(40L);
        assertThat(result.instructorProfile().name()).isEqualTo("김강사");
        assertThat(result.instructorProfile().profileImageUrl()).isEqualTo("https://example.com/instructor.png");
        assertThat(result.instructorProfile().gender()).isSameAs(Gender.FEMALE);
        assertThat(result.instructorProfile().birthYear()).isEqualTo(1998);
        assertThat(result.instructorProfile().level()).isEqualTo(3);
        assertThat(result.lessonId()).isNull();
        assertThat(result.priceSummary().lessonPriceAmount()).isEqualTo(80_000);
        assertThat(result.priceSummary().resortPassFeeAmount()).isEqualTo(20_000);
        assertThat(result.priceSummary().totalPaymentAmount()).isEqualTo(100_000);
        verifyNoInteractions(lessonRepository);
    }

    @Test
    void getStatus는_최종확인_단계의_서버기준_절대_진행률을_반환한다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest currentRequest = matchingRequest(10L, member(1L, "현재 요청자", MemberRole.CONSUMER));
        MatchingRequest otherRequest = matchingRequest(11L, member(2L, "다른 요청자", MemberRole.CONSUMER));
        MatchingRequestGroup group = matchingRequestGroup(20L);
        MatchingRequestGroupItem currentItem = matchingRequestGroupItem(30L, currentRequest, group);
        MatchingRequestGroupItem acceptedItem = matchingRequestGroupItem(31L, otherRequest, group);
        acceptedItem.accept(Instant.parse("2026-07-07T00:02:00Z"));
        MatchingOffer offer = acceptedOffer(50L, instructorProfile(40L), group);
        currentRequest.markMatched(offer);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(currentRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(currentItem));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdOrderByIdAsc(20L))
                .thenReturn(List.of(currentItem, acceptedItem));
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L))
                .thenReturn(Optional.of(offerPriceSnapshot(offer, 80_000, 20_000)));
        when(matchingStatusResolver.resolve(
                currentRequest,
                Optional.of(group),
                Optional.of(currentItem),
                Optional.of(offer),
                Optional.empty()
        )).thenReturn(MatchingStatus.WAITING_FOR_CONFIRMATION);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.progressSummary().acceptedRequesterCount()).isEqualTo(1);
        assertThat(result.progressSummary().totalRequesterCount()).isEqualTo(2);
        assertThat(result.progressSummary().paidRequesterCount()).isNull();
    }

    @Test
    void getStatus는_결제_단계의_서버기준_절대_진행률을_반환한다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest currentRequest = matchingRequest(10L, member(1L, "현재 요청자", MemberRole.CONSUMER));
        MatchingRequest otherRequest = matchingRequest(11L, member(2L, "다른 요청자", MemberRole.CONSUMER));
        MatchingRequestGroup group = matchingRequestGroup(20L);
        group.markPaymentPending();
        MatchingRequestGroupItem currentItem = matchingRequestGroupItem(30L, currentRequest, group);
        MatchingOffer offer = acceptedOffer(50L, instructorProfile(40L), group);
        MatchingRequestPayment pendingPayment = matchingRequestPayment(
                60L,
                currentRequest,
                offer,
                MatchingRequestPaymentStatus.PENDING,
                PAYMENT_EXPIRES_AT
        );
        MatchingRequestPayment completedPayment = matchingRequestPayment(
                61L,
                otherRequest,
                offer,
                MatchingRequestPaymentStatus.COMPLETED,
                PAYMENT_EXPIRES_AT
        );
        currentRequest.markMatched(offer);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(currentRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(currentItem));
        when(matchingRequestPaymentRepository.findByMatchingRequestIdAndMatchingOfferId(10L, 50L))
                .thenReturn(Optional.of(pendingPayment));
        when(matchingRequestPaymentRepository.findByMatchingOfferIdOrderByMatchingRequestIdAsc(50L))
                .thenReturn(List.of(pendingPayment, completedPayment));
        when(matchingStatusResolver.resolve(
                currentRequest,
                Optional.of(group),
                Optional.of(currentItem),
                Optional.of(offer),
                Optional.of(pendingPayment)
        )).thenReturn(MatchingStatus.PAYMENT_PENDING);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.progressSummary().acceptedRequesterCount()).isNull();
        assertThat(result.progressSummary().totalRequesterCount()).isEqualTo(2);
        assertThat(result.progressSummary().paidRequesterCount()).isEqualTo(1);
    }

    @Test
    void getStatus는_가격이_필요한_상태에서_제안스냅샷이_없으면_INTERNAL_ERROR를_던진다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest matchingRequest = matchingRequest(10L, member(1L, "요청자", MemberRole.CONSUMER));
        MatchingRequestGroup group = matchingRequestGroup(20L);
        MatchingRequestGroupItem item = matchingRequestGroupItem(30L, matchingRequest, group);
        MatchingOffer offer = acceptedOffer(50L, instructorProfile(40L), group);
        matchingRequest.markMatched(offer);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdOrderByIdAsc(20L))
                .thenReturn(List.of(item));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.empty()
        )).thenReturn(MatchingStatus.WAITING_FOR_CONFIRMATION);
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isSameAs(CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    void getStatus는_본인확인_완료후_다른_대표소비자_대기중에도_제안가격을_반환한다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest matchingRequest = matchingRequest(10L, member(1L, "요청자", MemberRole.CONSUMER));
        MatchingRequestGroup group = matchingRequestGroup(20L);
        MatchingRequestGroupItem item = matchingRequestGroupItem(30L, matchingRequest, group);
        item.accept(Instant.parse("2026-07-07T00:02:00Z"));
        MatchingOffer offer = acceptedOffer(50L, instructorProfile(40L), group);
        matchingRequest.markMatched(offer);
        MatchingOfferPriceSnapshot offerPriceSnapshot = offerPriceSnapshot(offer, 80_000, 20_000);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdOrderByIdAsc(20L))
                .thenReturn(List.of(item));
        when(matchingOfferPriceSnapshotRepository.findByMatchingOfferId(50L))
                .thenReturn(Optional.of(offerPriceSnapshot));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.empty()
        )).thenReturn(MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_OTHER_CONFIRMATIONS);
        assertThat(result.priceSummary().totalPaymentAmount()).isEqualTo(100_000);
    }

    @Test
    void getStatus는_결제대기_상태여도_무기한정책으로_expiresAt을_비운다() {
        MatchingStatusQueryService service = createService();
        Member consumer = member(1L, "요청자", MemberRole.CONSUMER);
        MatchingRequest matchingRequest = matchingRequest(10L, consumer);
        MatchingRequestGroup group = matchingRequestGroup(20L);
        MatchingRequestGroupItem item = matchingRequestGroupItem(30L, matchingRequest, group);
        MatchingOffer offer = acceptedOffer(50L, instructorProfile(40L), group);
        MatchingRequestPayment payment = matchingRequestPayment(
                60L,
                matchingRequest,
                offer,
                MatchingRequestPaymentStatus.PENDING,
                PAYMENT_EXPIRES_AT
        );
        matchingRequest.markMatched(offer);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestPaymentRepository.findByMatchingRequestIdAndMatchingOfferId(10L, 50L))
                .thenReturn(Optional.of(payment));
        when(matchingRequestPaymentRepository.findByMatchingOfferIdOrderByMatchingRequestIdAsc(50L))
                .thenReturn(List.of(payment));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.of(payment)
        )).thenReturn(MatchingStatus.PAYMENT_PENDING);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.PAYMENT_PENDING);
        assertThat(result.paymentStatus()).isSameAs(MatchingRequestPaymentStatus.PENDING);
        assertThat(result.expiresAt()).isNull();
        assertThat(result.lessonId()).isNull();
        assertThat(result.priceSummary().lessonPriceAmount()).isEqualTo(80_000);
        assertThat(result.priceSummary().resortPassFeeAmount()).isEqualTo(20_000);
        assertThat(result.priceSummary().totalPaymentAmount()).isEqualTo(100_000);
        verifyNoInteractions(lessonRepository);
    }

    @Test
    void getStatus는_본인결제_완료후_다른_결제_대기중에도_요청가격을_반환한다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest matchingRequest = matchingRequest(10L, member(1L, "요청자", MemberRole.CONSUMER));
        MatchingRequestGroup group = matchingRequestGroup(20L);
        group.markPaymentPending();
        MatchingRequestGroupItem item = matchingRequestGroupItem(30L, matchingRequest, group);
        item.accept(Instant.parse("2026-07-07T00:02:00Z"));
        MatchingOffer offer = acceptedOffer(50L, instructorProfile(40L), group);
        MatchingRequestPayment payment = matchingRequestPayment(
                60L,
                matchingRequest,
                offer,
                MatchingRequestPaymentStatus.COMPLETED,
                PAYMENT_EXPIRES_AT
        );
        matchingRequest.markMatched(offer);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestPaymentRepository.findByMatchingRequestIdAndMatchingOfferId(10L, 50L))
                .thenReturn(Optional.of(payment));
        when(matchingRequestPaymentRepository.findByMatchingOfferIdOrderByMatchingRequestIdAsc(50L))
                .thenReturn(List.of(payment));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.of(payment)
        )).thenReturn(MatchingStatus.WAITING_FOR_OTHER_PAYMENTS);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.WAITING_FOR_OTHER_PAYMENTS);
        assertThat(result.priceSummary().lessonPriceAmount()).isEqualTo(80_000);
        assertThat(result.priceSummary().resortPassFeeAmount()).isEqualTo(20_000);
        assertThat(result.priceSummary().totalPaymentAmount()).isEqualTo(100_000);
    }

    @Test
    void getStatus는_강습이_생성된_상태이면_lessonId를_반환한다() {
        MatchingStatusQueryService service = createService();
        Member consumer = member(1L, "요청자", MemberRole.CONSUMER);
        MatchingRequest matchingRequest = matchingRequest(10L, consumer);
        MatchingRequestGroup group = matchingRequestGroup(20L);
        MatchingRequestGroupItem item = matchingRequestGroupItem(30L, matchingRequest, group);
        item.accept(Instant.parse("2026-07-07T00:02:00Z"));
        MatchingOffer offer = acceptedOffer(50L, instructorProfile(40L), group);
        MatchingRequestPayment payment = matchingRequestPayment(
                60L,
                matchingRequest,
                offer,
                MatchingRequestPaymentStatus.COMPLETED,
                PAYMENT_EXPIRES_AT
        );
        Lesson lesson = lesson(70L, offer);
        matchingRequest.markMatched(offer);
        matchingRequest.confirm();
        group.confirm();

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestPaymentRepository.findByMatchingRequestIdAndMatchingOfferId(10L, 50L))
                .thenReturn(Optional.of(payment));
        when(lessonRepository.findByMatchingOfferId(50L)).thenReturn(Optional.of(lesson));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(group),
                Optional.of(item),
                Optional.of(offer),
                Optional.of(payment)
        )).thenReturn(MatchingStatus.CONFIRMED);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.CONFIRMED);
        assertThat(result.paymentStatus()).isSameAs(MatchingRequestPaymentStatus.COMPLETED);
        assertThat(result.expiresAt()).isNull();
        assertThat(result.lessonId()).isEqualTo(70L);
        assertThat(result.priceSummary().totalPaymentAmount()).isEqualTo(100_000);
    }

    @Test
    void getStatus는_재매칭후_요청에_과거제안이_남아있어도_현재그룹의_최신제안을_사용한다() {
        MatchingStatusQueryService service = createService();
        MatchingRequest matchingRequest = matchingRequest(10L, member(1L, "요청자", MemberRole.CONSUMER));
        MatchingRequestGroup oldGroup = matchingRequestGroup(20L);
        MatchingOffer oldOffer = acceptedOffer(50L, instructorProfile(40L), oldGroup);
        matchingRequest.markMatched(oldOffer);
        matchingRequest.rematchAfterConsumerRejected();
        matchingRequest.markGrouped();

        MatchingRequestGroup currentGroup = exposedMatchingRequestGroup(21L);
        MatchingRequestGroupItem currentItem = MatchingRequestGroupItem.createNotRequested(
                matchingRequest,
                currentGroup
        );
        ReflectionTestUtils.setField(currentItem, "id", 31L);
        MatchingOffer currentOffer = offeredOffer(51L, instructorProfile(41L), currentGroup);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(currentItem));
        when(matchingOfferRepository.findFirstByMatchingRequestGroupIdOrderByIdDesc(21L))
                .thenReturn(Optional.of(currentOffer));
        when(matchingStatusResolver.resolve(
                matchingRequest,
                Optional.of(currentGroup),
                Optional.of(currentItem),
                Optional.of(currentOffer),
                Optional.empty()
        )).thenReturn(MatchingStatus.WAITING_FOR_INSTRUCTOR);

        MatchingStatusQueryResult result = service.getStatus(1L, 10L);

        assertThat(result.offerStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        assertThat(result.priceSummary()).isNull();
        verify(matchingRequestPaymentRepository).findByMatchingRequestIdAndMatchingOfferId(10L, 51L);
    }

    private MatchingStatusQueryService createService() {
        return new MatchingStatusQueryService(
                matchingRequestRepository,
                matchingRequestGroupItemRepository,
                matchingOfferRepository,
                matchingOfferPriceSnapshotRepository,
                matchingRequestPaymentRepository,
                lessonRepository,
                matchingStatusResolver,
                new MatchingTimeoutPolicy()
        );
    }

    private MatchingRequest matchingRequest(Long id, Member member) {
        MatchingRequest matchingRequest = MatchingRequest.createUnlimitedSearch(
                member,
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                List.of(120, 180),
                true
        );
        ReflectionTestUtils.setField(matchingRequest, "id", id);
        return matchingRequest;
    }

    private MatchingRequestGroup matchingRequestGroup(Long id) {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        group.markInstructorAccepted();
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private MatchingRequestGroup exposedMatchingRequestGroup(Long id) {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private MatchingRequestGroupItem matchingRequestGroupItem(
            Long id,
            MatchingRequest matchingRequest,
            MatchingRequestGroup group
    ) {
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(matchingRequest, group);
        item.requestConfirmation();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private MatchingOffer acceptedOffer(
            Long id,
            InstructorProfile instructorProfile,
            MatchingRequestGroup group
    ) {
        MatchingOffer offer = MatchingOffer.create(instructorProfile, group, OFFER_EXPOSED_AT);
        offer.accept(OFFER_RESPONDED_AT);
        ReflectionTestUtils.setField(offer, "id", id);
        return offer;
    }

    private MatchingOffer offeredOffer(
            Long id,
            InstructorProfile instructorProfile,
            MatchingRequestGroup group
    ) {
        MatchingOffer offer = MatchingOffer.create(instructorProfile, group, OFFER_EXPOSED_AT);
        ReflectionTestUtils.setField(offer, "id", id);
        return offer;
    }

    private MatchingRequestPayment matchingRequestPayment(
            Long id,
            MatchingRequest matchingRequest,
            MatchingOffer offer,
            MatchingRequestPaymentStatus status,
            Instant paymentExpiresAt
    ) {
        MatchingRequestPayment payment = newInstance(MatchingRequestPayment.class);
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(payment, "matchingOffer", offer);
        ReflectionTestUtils.setField(payment, "matchingRequestPriceSnapshot", requestPriceSnapshot());
        ReflectionTestUtils.setField(payment, "amount", 120_000);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "paymentRequestedAt", Instant.parse("2026-07-07T00:05:00Z"));
        ReflectionTestUtils.setField(payment, "paymentExpiresAt", paymentExpiresAt);
        return payment;
    }

    private MatchingOfferPriceSnapshot offerPriceSnapshot(
            MatchingOffer offer,
            int lessonPriceAmount,
            int resortPassFeeAmount
    ) {
        MatchingOfferPriceSnapshot snapshot = newInstance(MatchingOfferPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "matchingOffer", offer);
        ReflectionTestUtils.setField(snapshot, "consumerTotalAmount", lessonPriceAmount);
        ReflectionTestUtils.setField(snapshot, "resortPassFeeAmount", resortPassFeeAmount);
        ReflectionTestUtils.setField(
                snapshot,
                "totalPaymentAmount",
                lessonPriceAmount + resortPassFeeAmount
        );
        return snapshot;
    }

    private MatchingRequestPriceSnapshot requestPriceSnapshot() {
        MatchingRequestPriceSnapshot snapshot = newInstance(MatchingRequestPriceSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "lessonPriceAmount", 80_000);
        ReflectionTestUtils.setField(snapshot, "resortPassFeeAmount", 20_000);
        ReflectionTestUtils.setField(snapshot, "totalPaymentAmount", 100_000);
        return snapshot;
    }

    private Lesson lesson(Long id, MatchingOffer offer) {
        Lesson lesson = newInstance(Lesson.class);
        ReflectionTestUtils.setField(lesson, "id", id);
        ReflectionTestUtils.setField(lesson, "matchingOffer", offer);
        return lesson;
    }

    private InstructorProfile instructorProfile(Long id) {
        InstructorProfile instructorProfile = InstructorProfile.create(
                member(4L, "강사닉네임", MemberRole.INSTRUCTOR),
                "김강사",
                "010-1234-5678",
                Gender.FEMALE,
                LocalDate.of(1998, 3, 1),
                "친절한 강사입니다.",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(instructorProfile, "id", id);
        return instructorProfile;
    }

    private Member member(Long id, String nickname, MemberRole role) {
        Member member = Member.create(nickname, "https://example.com/instructor.png", role, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Resort resort() {
        Resort resort = newInstance(Resort.class);
        ReflectionTestUtils.setField(resort, "code", "HIGH1");
        ReflectionTestUtils.setField(resort, "name", "하이원리조트");
        ReflectionTestUtils.setField(resort, "displayName", "하이원");
        return resort;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
