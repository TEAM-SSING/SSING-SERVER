package org.sopt.ssingserver.domain.lesson.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.dto.realtime.LessonRealtimeEventType;
import org.sopt.ssingserver.domain.lesson.dto.request.LessonCancellationRequest;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonCancellationResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancelReason;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancellationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeDelivery;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventPublisher;
import org.sopt.ssingserver.domain.lesson.repository.LessonCancellationRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.BusinessValidationException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LessonCancellationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-10T01:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonParticipantRepository lessonParticipantRepository;

    @Mock
    private LessonCancellationRepository lessonCancellationRepository;

    @Mock
    private LessonRealtimeEventPublisher lessonRealtimeEventPublisher;

    @Test
    void cancel은_대표소비자가_CONFIRMED_강습을_취소한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        ArgumentCaptor<LessonCancellation> cancellationCaptor = ArgumentCaptor.forClass(LessonCancellation.class);
        ArgumentCaptor<List<LessonRealtimeDelivery>> eventCaptor = ArgumentCaptor.forClass(List.class);

        LessonCancellationResponse response = service.cancel(
                currentConsumer(1L),
                500L,
                new LessonCancellationRequest(LessonCancelReason.SCHEDULE_CHANGED, null)
        );

        assertThat(response.lessonId()).isEqualTo(500L);
        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CANCELED);
        assertThat(response.canceledAt().toString()).isEqualTo("2026-07-10T10:00+09:00");
        assertThat(fixture.lesson().getStatus()).isSameAs(LessonStatus.CANCELED);
        assertThat(fixture.lesson().getCanceledAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(fixture.matchingRequests())
                .extracting(MatchingRequest::getStatus)
                .containsOnly(MatchingRequestStatus.CANCELED);
        assertThat(fixture.matchingRequests())
                .extracting(MatchingRequest::getCanceledAt)
                .containsOnlyNulls();

        verify(lessonCancellationRepository).save(cancellationCaptor.capture());
        LessonCancellation savedCancellation = cancellationCaptor.getValue();
        assertThat(savedCancellation.getLesson()).isSameAs(fixture.lesson());
        assertThat(savedCancellation.getMatchingRequest()).isSameAs(fixture.matchingRequests().getFirst());
        assertThat(savedCancellation.getMember().getId()).isEqualTo(1L);
        assertThat(savedCancellation.getCanceledBy()).isSameAs(LessonCancellationActor.CONSUMER);
        assertThat(savedCancellation.getCancelReason()).isEqualTo("일정 변경");
        assertThat(savedCancellation.getCanceledAt()).isEqualTo(FIXED_CLOCK.instant());

        verify(lessonRealtimeEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).hasSize(3);
        assertThat(eventCaptor.getValue())
                .allSatisfy(delivery -> {
                    assertThat(delivery.event().eventType()).isSameAs(LessonRealtimeEventType.LESSON_CANCELED);
                    assertThat(delivery.event().lessonStatus()).isSameAs(LessonStatus.CANCELED);
                });
    }

    @Test
    void cancel은_담당강사가_CONFIRMED_강습을_취소한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        ArgumentCaptor<LessonCancellation> cancellationCaptor = ArgumentCaptor.forClass(LessonCancellation.class);

        LessonCancellationResponse response = service.cancel(
                currentInstructor(3L),
                500L,
                new LessonCancellationRequest(LessonCancelReason.CONSUMER_NOT_MET, null)
        );

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CANCELED);
        assertThat(fixture.lesson().getCanceledAt()).isEqualTo(FIXED_CLOCK.instant());
        verify(lessonCancellationRepository).save(cancellationCaptor.capture());
        assertThat(cancellationCaptor.getValue().getMatchingRequest()).isNull();
        assertThat(cancellationCaptor.getValue().getMember().getId()).isEqualTo(3L);
        assertThat(cancellationCaptor.getValue().getCanceledBy()).isSameAs(LessonCancellationActor.INSTRUCTOR);
        assertThat(cancellationCaptor.getValue().getCancelReason()).isEqualTo("강습생을 못 만났어요");
        verify(lessonRealtimeEventPublisher).publish(any());
    }

    @Test
    void cancel은_대표소비자가_강사를_못_만난_사유로_CONFIRMED_강습을_취소한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        ArgumentCaptor<LessonCancellation> cancellationCaptor = ArgumentCaptor.forClass(LessonCancellation.class);

        service.cancel(
                currentConsumer(1L),
                500L,
                new LessonCancellationRequest(LessonCancelReason.INSTRUCTOR_NOT_MET, null)
        );

        verify(lessonCancellationRepository).save(cancellationCaptor.capture());
        assertThat(cancellationCaptor.getValue().getCanceledBy()).isSameAs(LessonCancellationActor.CONSUMER);
        assertThat(cancellationCaptor.getValue().getCancelReason()).isEqualTo("강사님을 못 만났어요");
    }

    @Test
    void cancel은_대표소비자가_강습생을_못_만난_사유를_선택하면_검증에_실패한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);

        assertThatExceptionOfType(BusinessValidationException.class)
                .isThrownBy(() -> service.cancel(
                        currentConsumer(1L),
                        500L,
                        new LessonCancellationRequest(LessonCancelReason.CONSUMER_NOT_MET, null)
                ))
                .satisfies(exception -> assertThat(exception.getErrors())
                        .containsEntry("cancelReason", "소비자는 '강습생을 못 만났어요' 사유를 선택할 수 없습니다."));
        verify(lessonCancellationRepository, never()).save(any());
        verify(lessonRealtimeEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_담당강사가_강사를_못_만난_사유를_선택하면_검증에_실패한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);

        assertThatExceptionOfType(BusinessValidationException.class)
                .isThrownBy(() -> service.cancel(
                        currentInstructor(3L),
                        500L,
                        new LessonCancellationRequest(LessonCancelReason.INSTRUCTOR_NOT_MET, null)
                ))
                .satisfies(exception -> assertThat(exception.getErrors())
                        .containsEntry("cancelReason", "강사는 '강사를 못 만났어요' 사유를 선택할 수 없습니다."));
        verify(lessonCancellationRepository, never()).save(any());
        verify(lessonRealtimeEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_담당강사나_대표소비자가_아니면_FORBIDDEN으로_실패한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(
                        currentConsumer(99L),
                        500L,
                        new LessonCancellationRequest(LessonCancelReason.SCHEDULE_CHANGED, null)
                ))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isSameAs(CommonErrorCode.FORBIDDEN));
        verify(lessonCancellationRepository, never()).save(any());
        verify(lessonRealtimeEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_CONFIRMED가_아닌_상태면_취소를_거절한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.IN_PROGRESS);
        givenLesson(fixture);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(
                        currentConsumer(1L),
                        500L,
                        new LessonCancellationRequest(LessonCancelReason.SCHEDULE_CHANGED, null)
                ))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(LessonErrorCode.LESSON_CANCEL_NOT_ALLOWED));
        verify(lessonCancellationRepository, never()).save(any());
        verify(lessonRealtimeEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_이미_취소한_회원의_중복_요청이면_취소를_거절한다() {
        LessonCancellationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        when(lessonCancellationRepository.existsByLessonIdAndMemberId(500L, 1L)).thenReturn(true);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(
                        currentConsumer(1L),
                        500L,
                        new LessonCancellationRequest(LessonCancelReason.SCHEDULE_CHANGED, null)
                ))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(LessonErrorCode.LESSON_CANCEL_NOT_ALLOWED));
        verify(lessonCancellationRepository, never()).save(any());
        verify(lessonRealtimeEventPublisher, never()).publish(any());
    }

    @Test
    void cancel은_강습이_없으면_LESSON_NOT_FOUND로_실패한다() {
        LessonCancellationService service = createService();
        when(lessonRepository.findByIdForUpdate(500L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.cancel(
                        currentConsumer(1L),
                        500L,
                        new LessonCancellationRequest(LessonCancelReason.SCHEDULE_CHANGED, null)
                ))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(LessonErrorCode.LESSON_NOT_FOUND));
        verify(lessonParticipantRepository, never()).findDistinctMatchingRequestsByLessonId(any());
    }

    private LessonCancellationService createService() {
        return new LessonCancellationService(
                lessonRepository,
                lessonParticipantRepository,
                lessonCancellationRepository,
                new LessonRealtimeEventFactory(),
                lessonRealtimeEventPublisher,
                new LessonAfterCommitExecutor(),
                FIXED_CLOCK
        );
    }

    private void givenLesson(Fixture fixture) {
        when(lessonRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(fixture.lesson()));
        when(lessonParticipantRepository.findDistinctMatchingRequestsByLessonId(500L))
                .thenReturn(fixture.matchingRequests());
    }

    private CurrentMember currentConsumer(Long memberId) {
        return new CurrentMember(memberId, MemberRole.CONSUMER, MemberStatus.ACTIVE, null);
    }

    private CurrentMember currentInstructor(Long memberId) {
        return new CurrentMember(
                memberId,
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE,
                InstructorApprovalStatus.APPROVED
        );
    }

    private Fixture fixture(LessonStatus lessonStatus) {
        Member consumer = member(1L, "김소비", MemberRole.CONSUMER);
        Member otherConsumer = member(2L, "박소비", MemberRole.CONSUMER);
        Member instructorMember = member(3L, "강사닉", MemberRole.INSTRUCTOR);
        InstructorProfile instructorProfile = instructorProfile(200L, instructorMember);
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        ReflectionTestUtils.setField(group, "id", 250L);
        MatchingOffer offer = MatchingOffer.create(instructorProfile, group, Instant.parse("2026-07-10T00:00:00Z"));
        ReflectionTestUtils.setField(offer, "id", 300L);
        MatchingRequest myRequest = matchingRequest(10L, consumer, 3);
        MatchingRequest otherRequest = matchingRequest(11L, otherConsumer, 2);
        Lesson lesson = Lesson.createImmediateConfirmed(
                instructorProfile,
                resort(),
                offer,
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                5,
                120,
                Instant.parse("2026-07-10T00:00:00Z")
        );
        ReflectionTestUtils.setField(lesson, "id", 500L);
        ReflectionTestUtils.setField(lesson, "status", lessonStatus);
        List<LessonParticipant> participants = List.of(
                lessonParticipant(100L, lesson, myRequest, 38, Gender.MALE),
                lessonParticipant(101L, lesson, myRequest, 12, Gender.FEMALE),
                lessonParticipant(102L, lesson, otherRequest, 9, Gender.MALE)
        );
        return new Fixture(lesson, participants, List.of(myRequest, otherRequest));
    }

    private MatchingRequest matchingRequest(
            Long id,
            Member member,
            int headcount
    ) {
        MatchingRequest matchingRequest = MatchingRequest.createUnlimitedSearch(
                member,
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                headcount,
                List.of(120),
                true
        );
        ReflectionTestUtils.setField(matchingRequest, "id", id);
        matchingRequest.confirm();
        return matchingRequest;
    }

    private LessonParticipant lessonParticipant(
            Long id,
            Lesson lesson,
            MatchingRequest matchingRequest,
            int age,
            Gender gender
    ) {
        LessonParticipant participant = construct(LessonParticipant.class);
        ReflectionTestUtils.setField(participant, "id", id);
        ReflectionTestUtils.setField(participant, "lesson", lesson);
        ReflectionTestUtils.setField(participant, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(participant, "age", age);
        ReflectionTestUtils.setField(participant, "gender", gender);
        return participant;
    }

    private InstructorProfile instructorProfile(
            Long id,
            Member member
    ) {
        InstructorProfile profile = InstructorProfile.create(
                member,
                "김강사",
                "010-1234-5678",
                Gender.MALE,
                LocalDate.of(1999, 1, 1),
                "친절한 강사입니다.",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    private Member member(
            Long id,
            String nickname,
            MemberRole role
    ) {
        Member member = Member.create(nickname, "https://example.com/profile.png", role, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Resort resort() {
        Resort resort = construct(Resort.class);
        ReflectionTestUtils.setField(resort, "id", 1L);
        ReflectionTestUtils.setField(resort, "code", "HIGH1");
        ReflectionTestUtils.setField(resort, "name", "하이원리조트");
        ReflectionTestUtils.setField(resort, "displayName", "하이원");
        return resort;
    }

    private <T> T construct(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
            Lesson lesson,
            List<LessonParticipant> participants,
            List<MatchingRequest> matchingRequests
    ) {
    }
}
