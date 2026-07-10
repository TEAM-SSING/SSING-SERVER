package org.sopt.ssingserver.domain.lesson.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.sopt.ssingserver.domain.lesson.dto.response.InstructorLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancellationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationStatus;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.domain.lesson.mapper.ConsumerLessonDetailResponseMapper;
import org.sopt.ssingserver.domain.lesson.mapper.InstructorLessonDetailResponseMapper;
import org.sopt.ssingserver.domain.lesson.repository.LessonCancellationRepository;
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
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InstructorLessonDetailTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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
    private LessonCancellationRepository lessonCancellationRepository;

    @Mock
    private MatchingRequestPaymentRepository matchingRequestPaymentRepository;

    @Test
    void getDetail은_CONFIRMED_강습의_강사와_팀단위_준비상태와_가격을_반환한다() throws Exception {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenOwnedLesson(fixture);
        LessonStartConfirmation instructorConfirmation = confirmation(
                fixture.lesson(),
                fixture.instructorMember(),
                null,
                LessonStartConfirmationActor.INSTRUCTOR
        );
        LessonStartConfirmation firstTeamConfirmation = confirmation(
                fixture.lesson(),
                fixture.consumer(),
                fixture.firstRequest(),
                LessonStartConfirmationActor.CONSUMER
        );
        when(lessonStartConfirmationRepository.findByLessonId(500L))
                .thenReturn(List.of(instructorConfirmation, firstTeamConfirmation));

        InstructorLessonDetailResponse response = service.getInstructorDetail(3L, 500L);
        JsonNode data = toJson(response);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CONFIRMED);
        InstructorLessonDetailResponse.ConfirmedStatusInfoResponse statusInfo =
                (InstructorLessonDetailResponse.ConfirmedStatusInfoResponse) response.statusInfo();
        InstructorLessonDetailResponse.LessonInfoResponse lessonInfo =
                (InstructorLessonDetailResponse.LessonInfoResponse) response.lessonInfo();

        assertThat(statusInfo.requiredCount()).isEqualTo(6);
        assertThat(statusInfo.confirmedCount()).isEqualTo(4);
        assertThat(statusInfo.currentActorConfirmed()).isTrue();
        assertThat(statusInfo.instructorConfirmed()).isTrue();
        assertThat(lessonInfo.representativeConsumerNames()).containsExactly("김소비", "박소비");
        assertThat(lessonInfo.totalLessonPrice()).isEqualTo(87_500);
        assertThat(response.matchingRequests()).hasSize(2);
        assertThat(data.get("matchingRequests").get(0).get("teamLessonPrice").asInt()).isEqualTo(40_000);
        assertThat(data.get("matchingRequests").get(1).get("teamLessonPrice").asInt()).isEqualTo(47_500);
        assertThat(data.get("statusInfo").get("currentActorConfirmed").asBoolean()).isTrue();
        assertThat(data.get("lessonInfo").has("representativeConsumerName")).isFalse();
        assertThat(data.get("lessonInfo").get("representativeConsumerNames")).hasSize(2);
        assertThat(data.get("matchingRequests").get(0).has("startConfirmed")).isTrue();
        assertThat(data.get("matchingRequests").get(0).has("confirmedAt")).isFalse();
    }

    @Test
    void getDetail은_IN_PROGRESS_강습의_시간값을_초단위로_계산한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(fixture.lesson(), "startedAt", Instant.parse("2026-07-10T00:00:00Z"));
        givenOwnedLesson(fixture);

        InstructorLessonDetailResponse response = service.getInstructorDetail(3L, 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.IN_PROGRESS);
        InstructorLessonDetailResponse.InProgressStatusInfoResponse statusInfo =
                (InstructorLessonDetailResponse.InProgressStatusInfoResponse) response.statusInfo();

        assertThat(statusInfo.elapsedSeconds()).isEqualTo(3600);
        assertThat(statusInfo.remainingSeconds()).isEqualTo(3600);
        assertThat(response.matchingRequests()).hasSize(2);
    }

    @Test
    void getDetail은_COMPLETED_강습에서_팀정보를_포함하고_전체가격을_반환한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.COMPLETED);
        ReflectionTestUtils.setField(fixture.lesson(), "startedAt", Instant.parse("2026-07-10T00:00:00Z"));
        ReflectionTestUtils.setField(fixture.lesson(), "completedAt", Instant.parse("2026-07-10T01:58:00Z"));
        givenOwnedLesson(fixture);

        InstructorLessonDetailResponse response = service.getInstructorDetail(3L, 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.COMPLETED);
        InstructorLessonDetailResponse.CompletedLessonInfoResponse lessonInfo =
                (InstructorLessonDetailResponse.CompletedLessonInfoResponse) response.lessonInfo();
        assertThat(lessonInfo.actualDurationMinutes()).isEqualTo(118);
        assertThat(lessonInfo.representativeConsumerNames()).containsExactly("김소비", "박소비");
        assertThat(lessonInfo.totalLessonPrice()).isEqualTo(87_500);
        assertThat(response.matchingRequests()).hasSize(2);
    }

    @Test
    void getDetail은_CANCELED_강습에서_마지막_취소정보를_반환한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CANCELED);
        givenOwnedLesson(fixture);
        LessonCancellation consumerCancellation = cancellation(
                fixture.lesson(),
                fixture.consumer(),
                fixture.firstRequest(),
                LessonCancellationActor.CONSUMER,
                Instant.parse("2026-07-10T00:30:00Z")
        );
        LessonCancellation instructorCancellation = cancellation(
                fixture.lesson(),
                fixture.instructorMember(),
                null,
                LessonCancellationActor.INSTRUCTOR,
                Instant.parse("2026-07-10T00:40:00Z")
        );
        when(lessonCancellationRepository.findByLessonId(500L))
                .thenReturn(List.of(consumerCancellation, instructorCancellation));

        InstructorLessonDetailResponse response = service.getInstructorDetail(3L, 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CANCELED);
        assertThat(response.cancelInfo().canceledBy().memberId()).isEqualTo(3L);
        assertThat(response.cancelInfo().canceledBy().name()).isEqualTo("김강사");
        assertThat(response.cancelInfo().cancelReason()).isEqualTo("일정 변경");
        assertThat(response.matchingRequests()).hasSize(2);
    }

    @Test
    void getDetail은_담당_강사가_아니면_FORBIDDEN으로_실패한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        when(lessonRepository.findWithDetailById(500L)).thenReturn(Optional.of(fixture.lesson()));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getInstructorDetail(99L, 500L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void getDetail은_팀가격이_없으면_가격정보_없음으로_실패한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        when(lessonRepository.findWithDetailById(500L)).thenReturn(Optional.of(fixture.lesson()));
        when(lessonParticipantRepository.findDetailParticipantsByLessonId(500L))
                .thenReturn(fixture.participants());
        when(matchingRequestPaymentRepository.findByMatchingOfferIdOrderByMatchingRequestIdAsc(300L))
                .thenReturn(List.of(payment(fixture.firstRequest(), fixture.offer(), 40_000)));
        when(lessonStartConfirmationRepository.findByLessonId(500L)).thenReturn(List.of());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getInstructorDetail(3L, 500L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(LessonErrorCode.LESSON_PRICE_NOT_FOUND));
    }

    private LessonDetailService createService() {
        LessonDetailReader lessonDetailReader = new LessonDetailReader(
                lessonRepository,
                lessonParticipantRepository,
                lessonCancellationRepository,
                lessonStartConfirmationRepository,
                matchingRequestPaymentRepository
        );
        ConsumerLessonDetailResponseMapper consumerResponseMapper = new ConsumerLessonDetailResponseMapper(FIXED_CLOCK);
        InstructorLessonDetailResponseMapper responseMapper = new InstructorLessonDetailResponseMapper(FIXED_CLOCK);
        return new LessonDetailService(lessonDetailReader, consumerResponseMapper, responseMapper);
    }

    private void givenOwnedLesson(Fixture fixture) {
        when(lessonRepository.findWithDetailById(500L)).thenReturn(Optional.of(fixture.lesson()));
        when(lessonParticipantRepository.findDetailParticipantsByLessonId(500L))
                .thenReturn(fixture.participants());
        when(matchingRequestPaymentRepository.findByMatchingOfferIdOrderByMatchingRequestIdAsc(300L))
                .thenReturn(List.of(
                        payment(fixture.firstRequest(), fixture.offer(), 40_000),
                        payment(fixture.secondRequest(), fixture.offer(), 47_500)
                ));
    }

    private Fixture fixture(LessonStatus lessonStatus) {
        Member consumer = member(1L, "김소비", MemberRole.CONSUMER);
        Member otherConsumer = member(2L, "박소비", MemberRole.CONSUMER);
        Member instructorMember = member(3L, "강사닉", MemberRole.INSTRUCTOR);
        InstructorProfile instructorProfile = instructorProfile(200L, instructorMember);
        Resort resort = resort();
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        ReflectionTestUtils.setField(group, "id", 250L);
        MatchingOffer offer = MatchingOffer.create(instructorProfile, group, Instant.parse("2026-07-10T00:00:00Z"));
        ReflectionTestUtils.setField(offer, "id", 300L);
        MatchingRequest firstRequest = matchingRequest(10L, consumer, 3);
        MatchingRequest secondRequest = matchingRequest(11L, otherConsumer, 2);
        Lesson lesson = Lesson.createImmediateConfirmed(
                instructorProfile,
                resort,
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
                lessonParticipant(100L, lesson, firstRequest, 38, Gender.MALE),
                lessonParticipant(101L, lesson, firstRequest, 12, Gender.FEMALE),
                lessonParticipant(102L, lesson, secondRequest, 9, Gender.MALE)
        );
        return new Fixture(lesson, offer, firstRequest, secondRequest, consumer, instructorMember, participants);
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
        LessonStartConfirmation confirmation = construct(LessonStartConfirmation.class);
        ReflectionTestUtils.setField(confirmation, "lesson", lesson);
        ReflectionTestUtils.setField(confirmation, "member", member);
        ReflectionTestUtils.setField(confirmation, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(confirmation, "actorType", actor);
        ReflectionTestUtils.setField(confirmation, "status", LessonStartConfirmationStatus.CONFIRMED);
        ReflectionTestUtils.setField(confirmation, "confirmedAt", Instant.parse("2026-07-10T00:55:00Z"));
        return confirmation;
    }

    private LessonCancellation cancellation(
            Lesson lesson,
            Member member,
            MatchingRequest matchingRequest,
            LessonCancellationActor actor,
            Instant canceledAt
    ) {
        LessonCancellation cancellation = construct(LessonCancellation.class);
        ReflectionTestUtils.setField(cancellation, "lesson", lesson);
        ReflectionTestUtils.setField(cancellation, "member", member);
        ReflectionTestUtils.setField(cancellation, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(cancellation, "canceledBy", actor);
        ReflectionTestUtils.setField(cancellation, "cancelReason", "일정 변경");
        ReflectionTestUtils.setField(cancellation, "canceledAt", canceledAt);
        return cancellation;
    }

    private MatchingRequestPayment payment(
            MatchingRequest matchingRequest,
            MatchingOffer offer,
            int amount
    ) {
        MatchingRequestPayment payment = construct(MatchingRequestPayment.class);
        ReflectionTestUtils.setField(payment, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(payment, "matchingOffer", offer);
        ReflectionTestUtils.setField(payment, "amount", amount);
        ReflectionTestUtils.setField(payment, "paymentRequestedAt", Instant.parse("2026-07-10T00:00:00Z"));
        return payment;
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
        ReflectionTestUtils.setField(profile, "level", 2);
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

    private JsonNode toJson(InstructorLessonDetailResponse response) {
        return OBJECT_MAPPER.valueToTree(response);
    }

    private record Fixture(
            Lesson lesson,
            MatchingOffer offer,
            MatchingRequest firstRequest,
            MatchingRequest secondRequest,
            Member consumer,
            Member instructorMember,
            List<LessonParticipant> participants
    ) {
    }
}
