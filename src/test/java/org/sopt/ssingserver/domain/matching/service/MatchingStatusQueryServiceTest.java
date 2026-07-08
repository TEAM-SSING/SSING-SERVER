package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchingStatusQueryServiceTest {

    private static final Instant MATCHING_EXPIRES_AT = Instant.parse("2026-07-07T00:10:00Z");
    private static final Instant PAYMENT_EXPIRES_AT = Instant.parse("2026-07-07T00:15:00Z");

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingRequestPaymentRepository matchingRequestPaymentRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private MatchingStatusResolver matchingStatusResolver;

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
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
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
        verifyNoInteractions(matchingOfferRepository);
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
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.empty());
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
        assertThat(result.instructorProfile().instructorId()).isEqualTo(40L);
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
        MatchingOffer offer = acceptedOffer(50L, instructorProfile, group);
        matchingRequest.markMatched(offer, MATCHING_EXPIRES_AT);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.empty());
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
        assertThat(result.expiresAt()).isEqualTo(MATCHING_EXPIRES_AT);
        assertThat(result.instructorProfile()).isNotNull();
        assertThat(result.instructorProfile().instructorId()).isEqualTo(40L);
        assertThat(result.instructorProfile().name()).isEqualTo("김강사");
        assertThat(result.instructorProfile().profileImageUrl()).isEqualTo("https://example.com/instructor.png");
        assertThat(result.instructorProfile().gender()).isSameAs(Gender.FEMALE);
        assertThat(result.instructorProfile().birthYear()).isEqualTo(1998);
        assertThat(result.lessonId()).isNull();
        verifyNoInteractions(lessonRepository);
    }

    @Test
    void getStatus는_결제대기_상태이면_paymentStatus와_결제만료시각을_반환한다() {
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
        matchingRequest.markMatched(offer, MATCHING_EXPIRES_AT);

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(payment));
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
        assertThat(result.expiresAt()).isEqualTo(PAYMENT_EXPIRES_AT);
        assertThat(result.lessonId()).isNull();
        verifyNoInteractions(lessonRepository);
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
        matchingRequest.markMatched(offer, MATCHING_EXPIRES_AT);
        matchingRequest.confirm();
        group.confirm();

        when(matchingRequestRepository.findById(10L)).thenReturn(Optional.of(matchingRequest));
        when(matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(item));
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(10L))
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
    }

    private MatchingStatusQueryService createService() {
        return new MatchingStatusQueryService(
                matchingRequestRepository,
                matchingRequestGroupItemRepository,
                matchingOfferRepository,
                matchingRequestPaymentRepository,
                lessonRepository,
                matchingStatusResolver
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
        MatchingOffer offer = MatchingOffer.create(instructorProfile, group, Instant.parse("2026-07-07T00:00:00Z"));
        offer.accept(Instant.parse("2026-07-07T00:01:00Z"));
        ReflectionTestUtils.setField(offer, "id", id);
        return offer;
    }

    private MatchingOffer offeredOffer(
            Long id,
            InstructorProfile instructorProfile,
            MatchingRequestGroup group
    ) {
        MatchingOffer offer = MatchingOffer.create(instructorProfile, group, Instant.parse("2026-07-07T00:00:00Z"));
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
        ReflectionTestUtils.setField(payment, "amount", 120_000);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "paymentRequestedAt", Instant.parse("2026-07-07T00:05:00Z"));
        ReflectionTestUtils.setField(payment, "paymentExpiresAt", paymentExpiresAt);
        return payment;
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
