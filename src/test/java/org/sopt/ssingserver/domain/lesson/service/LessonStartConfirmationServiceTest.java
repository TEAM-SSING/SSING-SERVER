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
import org.sopt.ssingserver.domain.lesson.dto.response.LessonStartConfirmationResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeDelivery;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventFactory;
import org.sopt.ssingserver.domain.lesson.realtime.LessonRealtimeEventPublisher;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonStartConfirmationRepository;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LessonStartConfirmationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-10T01:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonParticipantRepository lessonParticipantRepository;

    @Mock
    private LessonStartConfirmationRepository lessonStartConfirmationRepository;

    @Mock
    private LessonRealtimeEventPublisher lessonRealtimeEventPublisher;

    @Test
    void confirmStart는_대표소비자_확인후_아직_대기중이면_현재_확인현황을_반환한다() {
        LessonStartConfirmationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        when(lessonStartConfirmationRepository.findByLessonIdAndMemberId(500L, 1L)).thenReturn(Optional.empty());
        when(lessonStartConfirmationRepository.save(any(LessonStartConfirmation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStartConfirmationRepository.findByLessonId(500L))
                .thenReturn(List.of(confirmation(
                        fixture.lesson(),
                        fixture.consumer(),
                        fixture.myRequest(),
                        LessonStartConfirmationActor.CONSUMER
                )));

        LessonStartConfirmationResponse response = service.confirmStart(
                currentConsumer(1L),
                500L
        );

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CONFIRMED);
        assertThat(response.statusInfo().confirmedCount()).isEqualTo(3);
        assertThat(response.statusInfo().requiredCount()).isEqualTo(6);
        assertThat(response.statusInfo().currentActorConfirmed()).isTrue();
        assertThat(response.statusInfo().instructorConfirmed()).isFalse();
        verify(lessonRealtimeEventPublisher).publish(any());
    }

    @Test
    void confirmStart는_마지막_확인이면_강습을_시작한다() {
        LessonStartConfirmationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        when(lessonStartConfirmationRepository.findByLessonIdAndMemberId(500L, 1L)).thenReturn(Optional.empty());
        when(lessonStartConfirmationRepository.save(any(LessonStartConfirmation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStartConfirmationRepository.findByLessonId(500L))
                .thenReturn(List.of(
                        confirmation(
                                fixture.lesson(),
                                fixture.instructorMember(),
                                null,
                                LessonStartConfirmationActor.INSTRUCTOR
                        ),
                        confirmation(
                                fixture.lesson(),
                                fixture.consumer(),
                                fixture.myRequest(),
                                LessonStartConfirmationActor.CONSUMER
                        ),
                        confirmation(
                                fixture.lesson(),
                                fixture.otherConsumer(),
                                fixture.otherRequest(),
                                LessonStartConfirmationActor.CONSUMER
                        )
                ));

        LessonStartConfirmationResponse response = service.confirmStart(
                currentConsumer(1L),
                500L
        );

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.IN_PROGRESS);
        assertThat(response.startedAt().toString()).isEqualTo("2026-07-10T10:00+09:00");
        assertThat(fixture.lesson().getStatus()).isSameAs(LessonStatus.IN_PROGRESS);
        assertThat(fixture.lesson().getStartedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    void confirmStart는_이미_확인한_사용자면_중복저장하지_않고_성공한다() {
        LessonStartConfirmationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        LessonStartConfirmation existing = confirmation(
                fixture.lesson(),
                fixture.consumer(),
                fixture.myRequest(),
                LessonStartConfirmationActor.CONSUMER
        );
        when(lessonStartConfirmationRepository.findByLessonIdAndMemberId(500L, 1L))
                .thenReturn(Optional.of(existing));
        when(lessonStartConfirmationRepository.findByLessonId(500L)).thenReturn(List.of(existing));

        LessonStartConfirmationResponse response = service.confirmStart(currentConsumer(1L), 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CONFIRMED);
        assertThat(response.statusInfo().currentActorConfirmed()).isTrue();
        verify(lessonStartConfirmationRepository, never()).save(any());
    }

    @Test
    void confirmStart는_대표소비자나_강사가_아니면_FORBIDDEN으로_실패한다() {
        LessonStartConfirmationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.confirmStart(currentConsumer(99L), 500L))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isSameAs(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void confirmStart는_CONFIRMED가_아닌_상태면_시작확인을_거절한다() {
        LessonStartConfirmationService service = createService();
        Fixture fixture = fixture(LessonStatus.COMPLETED);
        givenLesson(fixture);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.confirmStart(currentConsumer(1L), 500L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(LessonErrorCode.LESSON_START_NOT_ALLOWED));
    }

    @Test
    void confirmStart는_소비자_confirmation을_올바른_actor와_matchingRequest로_저장한다() {
        LessonStartConfirmationService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenLesson(fixture);
        when(lessonStartConfirmationRepository.findByLessonIdAndMemberId(500L, 1L)).thenReturn(Optional.empty());
        when(lessonStartConfirmationRepository.save(any(LessonStartConfirmation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonStartConfirmationRepository.findByLessonId(500L)).thenReturn(List.of());
        ArgumentCaptor<LessonStartConfirmation> captor = ArgumentCaptor.forClass(LessonStartConfirmation.class);

        service.confirmStart(currentConsumer(1L), 500L);

        verify(lessonStartConfirmationRepository).save(captor.capture());
        LessonStartConfirmation saved = captor.getValue();
        assertThat(saved.getActorType()).isSameAs(LessonStartConfirmationActor.CONSUMER);
        assertThat(saved.getMember()).isSameAs(fixture.consumer());
        assertThat(saved.getMatchingRequest()).isSameAs(fixture.myRequest());
        assertThat(saved.getConfirmedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    private LessonStartConfirmationService createService() {
        return new LessonStartConfirmationService(
                lessonRepository,
                lessonParticipantRepository,
                lessonStartConfirmationRepository,
                new LessonRealtimeEventFactory(),
                lessonRealtimeEventPublisher,
                new LessonAfterCommitExecutor(),
                FIXED_CLOCK
        );
    }

    private void givenLesson(Fixture fixture) {
        when(lessonRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(fixture.lesson()));
        when(lessonParticipantRepository.findDistinctMatchingRequestsByLessonId(500L))
                .thenReturn(List.of(fixture.myRequest(), fixture.otherRequest()));
    }

    private CurrentMember currentConsumer(Long memberId) {
        return new CurrentMember(memberId, MemberRole.CONSUMER, MemberStatus.ACTIVE, null);
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
        if (lessonStatus == LessonStatus.IN_PROGRESS) {
            ReflectionTestUtils.setField(lesson, "startedAt", Instant.parse("2026-07-10T00:30:00Z"));
        }
        List<LessonParticipant> participants = List.of(
                lessonParticipant(100L, lesson, myRequest, 38, Gender.MALE),
                lessonParticipant(101L, lesson, myRequest, 12, Gender.FEMALE),
                lessonParticipant(102L, lesson, otherRequest, 9, Gender.MALE)
        );
        return new Fixture(
                lesson,
                myRequest,
                otherRequest,
                consumer,
                otherConsumer,
                instructorMember,
                participants
        );
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

    private LessonStartConfirmation confirmation(
            Lesson lesson,
            Member member,
            MatchingRequest matchingRequest,
            LessonStartConfirmationActor actor
    ) {
        LessonStartConfirmation confirmation = actor == LessonStartConfirmationActor.INSTRUCTOR
                ? LessonStartConfirmation.confirmInstructor(lesson, member, FIXED_CLOCK.instant())
                : LessonStartConfirmation.confirmConsumer(lesson, member, matchingRequest, FIXED_CLOCK.instant());
        return confirmation;
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
            MatchingRequest myRequest,
            MatchingRequest otherRequest,
            Member consumer,
            Member otherConsumer,
            Member instructorMember,
            List<LessonParticipant> participants
    ) {
    }
}
