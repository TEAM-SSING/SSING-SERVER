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
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LessonDetailServiceTest {

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
    void getDetail은_CONFIRMED_강습의_강사와_팀단위_준비상태를_반환한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenAccessibleLesson(fixture);
        LessonStartConfirmation instructorConfirmation = confirmation(
                fixture.lesson(),
                fixture.instructorMember(),
                null,
                LessonStartConfirmationActor.INSTRUCTOR,
                Instant.parse("2026-07-10T00:50:00Z")
        );
        LessonStartConfirmation myTeamConfirmation = confirmation(
                fixture.lesson(),
                fixture.consumer(),
                fixture.myRequest(),
                LessonStartConfirmationActor.CONSUMER,
                Instant.parse("2026-07-10T00:55:00Z")
        );
        when(lessonStartConfirmationRepository.findByLessonId(500L))
                .thenReturn(List.of(instructorConfirmation, myTeamConfirmation));

        ConsumerLessonDetailResponse response = service.getDetail(1L, 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CONFIRMED);
        ConsumerLessonDetailResponse.ConfirmedStatusInfoResponse statusInfo =
                (ConsumerLessonDetailResponse.ConfirmedStatusInfoResponse) response.statusInfo();
        ConsumerLessonDetailResponse.LessonInfoResponse lessonInfo =
                (ConsumerLessonDetailResponse.LessonInfoResponse) response.lessonInfo();

        assertThat(statusInfo.requiredCount()).isEqualTo(3);
        assertThat(statusInfo.confirmedCount()).isEqualTo(2);
        assertThat(statusInfo.myTeamConfirmed()).isTrue();
        assertThat(statusInfo.instructorConfirmed()).isTrue();
        assertThat(lessonInfo.myTeamLessonPrice()).isEqualTo(40_000);
        assertThat(response.matchingRequests()).hasSize(2);
        ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse firstMatchingRequest =
                (ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse) response.matchingRequests().get(0);
        ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse secondMatchingRequest =
                (ConsumerLessonDetailResponse.ConfirmedMatchingRequestResponse) response.matchingRequests().get(1);
        assertThat(firstMatchingRequest.startConfirmed()).isTrue();
        assertThat(secondMatchingRequest.startConfirmed()).isFalse();
    }

    @Test
    void getDetail은_IN_PROGRESS_강습의_시간값을_초단위로_계산한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(fixture.lesson(), "startedAt", Instant.parse("2026-07-10T00:00:00Z"));
        givenAccessibleLesson(fixture);

        ConsumerLessonDetailResponse response = service.getDetail(1L, 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.IN_PROGRESS);
        ConsumerLessonDetailResponse.InProgressStatusInfoResponse statusInfo =
                (ConsumerLessonDetailResponse.InProgressStatusInfoResponse) response.statusInfo();

        assertThat(statusInfo.elapsedSeconds()).isEqualTo(3600);
        assertThat(statusInfo.remainingSeconds()).isEqualTo(3600);
        assertThat(statusInfo.serverTime()).isNotNull();
        assertThat(response.matchingRequests()).hasSize(2);
        assertThat(response.matchingRequests().get(0))
                .isInstanceOf(ConsumerLessonDetailResponse.MatchingRequestResponse.class);
    }

    @Test
    void getDetail은_CONFIRMED_응답에서_팀별_준비완료_필드를_포함한다() throws Exception {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenAccessibleLesson(fixture);
        when(lessonStartConfirmationRepository.findByLessonId(500L))
                .thenReturn(List.of(confirmation(
                        fixture.lesson(),
                        fixture.consumer(),
                        fixture.myRequest(),
                        LessonStartConfirmationActor.CONSUMER,
                        Instant.parse("2026-07-10T00:55:00Z")
                )));

        JsonNode data = toJson(service.getDetail(1L, 500L));

        assertThat(data.has("statusInfo")).isTrue();
        assertThat(data.has("cancelInfo")).isFalse();
        assertThat(data.has("matchingRequests")).isTrue();
        assertThat(data.get("matchingRequests").get(0).has("startConfirmed")).isTrue();
        assertThat(data.get("matchingRequests").get(0).has("confirmedAt")).isTrue();
    }

    @Test
    void getDetail은_IN_PROGRESS_응답에서_준비완료_필드를_제외한다() throws Exception {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(fixture.lesson(), "startedAt", Instant.parse("2026-07-10T00:00:00Z"));
        givenAccessibleLesson(fixture);

        JsonNode data = toJson(service.getDetail(1L, 500L));

        assertThat(data.has("statusInfo")).isTrue();
        assertThat(data.has("cancelInfo")).isFalse();
        assertThat(data.has("matchingRequests")).isTrue();
        assertThat(data.get("matchingRequests").get(0).has("startConfirmed")).isFalse();
        assertThat(data.get("matchingRequests").get(0).has("confirmedAt")).isFalse();
    }

    @Test
    void getDetail은_COMPLETED_응답에서_statusInfo_cancelInfo_matchingRequests를_제외한다() throws Exception {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.COMPLETED);
        ReflectionTestUtils.setField(fixture.lesson(), "startedAt", Instant.parse("2026-07-10T00:00:00Z"));
        ReflectionTestUtils.setField(fixture.lesson(), "completedAt", Instant.parse("2026-07-10T01:58:00Z"));
        givenAccessibleLesson(fixture);

        JsonNode data = toJson(service.getDetail(1L, 500L));

        assertThat(data.has("statusInfo")).isFalse();
        assertThat(data.has("cancelInfo")).isFalse();
        assertThat(data.has("matchingRequests")).isFalse();
        assertThat(data.get("lessonInfo").has("lessonDurationMinutes")).isTrue();
        assertThat(data.get("lessonInfo").has("scheduledAt")).isFalse();
        assertThat(data.get("lessonInfo").has("scheduledDurationMinutes")).isFalse();
    }

    @Test
    void getDetail은_CANCELED_응답에서_statusInfo_matchingRequests를_제외하고_cancelInfo를_포함한다() throws Exception {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CANCELED);
        LessonCancellation instructorCancellation = cancellation(
                fixture.lesson(),
                fixture.instructorMember(),
                null,
                LessonCancellationActor.INSTRUCTOR
        );
        givenAccessibleLesson(fixture);
        when(lessonCancellationRepository.findByLessonIdAndCanceledBy(500L, LessonCancellationActor.INSTRUCTOR))
                .thenReturn(List.of(instructorCancellation));

        JsonNode data = toJson(service.getDetail(1L, 500L));

        assertThat(data.has("statusInfo")).isFalse();
        assertThat(data.has("cancelInfo")).isTrue();
        assertThat(data.has("matchingRequests")).isFalse();
        assertThat(data.get("lessonInfo").has("lessonDurationMinutes")).isTrue();
        assertThat(data.get("lessonInfo").has("scheduledAt")).isFalse();
        assertThat(data.get("lessonInfo").has("scheduledDurationMinutes")).isFalse();
    }

    @Test
    void getDetail은_내팀_취소정보가_있으면_강습상태를_CANCELED로_반환한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CONFIRMED);
        givenAccessibleLesson(fixture);
        LessonCancellation myCancellation = cancellation(
                fixture.lesson(),
                fixture.consumer(),
                fixture.myRequest(),
                LessonCancellationActor.CONSUMER
        );
        when(lessonCancellationRepository.findByLessonIdAndMatchingRequestId(500L, 10L))
                .thenReturn(List.of(myCancellation));

        ConsumerLessonDetailResponse response = service.getDetail(1L, 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CANCELED);
        assertThat(response.cancelInfo().canceledBy().memberId()).isEqualTo(1L);
        assertThat(response.cancelInfo().canceledBy().name()).isEqualTo("김소비");
        assertThat(response.cancelInfo().cancelReason()).isEqualTo("일정 변경");
        assertThat(response.matchingRequests()).isNull();
    }

    @Test
    void getDetail은_내팀취소와_강사취소가_모두_있으면_최신_취소정보를_반환한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.CANCELED);
        givenAccessibleLesson(fixture);
        LessonCancellation myCancellation = cancellation(
                fixture.lesson(),
                fixture.consumer(),
                fixture.myRequest(),
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
        when(lessonCancellationRepository.findByLessonIdAndMatchingRequestId(500L, 10L))
                .thenReturn(List.of(myCancellation));
        when(lessonCancellationRepository.findByLessonIdAndCanceledBy(500L, LessonCancellationActor.INSTRUCTOR))
                .thenReturn(List.of(instructorCancellation));

        ConsumerLessonDetailResponse response = service.getDetail(1L, 500L);

        assertThat(response.lessonStatus()).isSameAs(LessonStatus.CANCELED);
        assertThat(response.cancelInfo().canceledBy().memberId()).isEqualTo(3L);
        assertThat(response.cancelInfo().canceledBy().name()).isEqualTo("김강사");
    }

    @Test
    void getDetail은_IN_PROGRESS인데_startedAt이_없으면_잘못된_강습상태로_실패한다() {
        LessonDetailService service = createService();
        Fixture fixture = fixture(LessonStatus.IN_PROGRESS);
        givenAccessibleLesson(fixture);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getDetail(1L, 500L))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isSameAs(LessonErrorCode.LESSON_INVALID_STATE));
    }

    private LessonDetailService createService() {
        ConsumerLessonDetailResponseMapper responseMapper = new ConsumerLessonDetailResponseMapper(FIXED_CLOCK);
        return new LessonDetailService(
                lessonRepository,
                lessonParticipantRepository,
                lessonCancellationRepository,
                lessonStartConfirmationRepository,
                matchingRequestPaymentRepository,
                responseMapper
        );
    }

    private void givenAccessibleLesson(Fixture fixture) {
        when(lessonRepository.findWithDetailById(500L)).thenReturn(Optional.of(fixture.lesson()));
        when(lessonParticipantRepository.findDetailParticipantsByLessonId(500L))
                .thenReturn(fixture.participants());
        when(lessonParticipantRepository.findMatchingRequestIdsByLessonIdAndMemberId(500L, 1L))
                .thenReturn(List.of(10L));
        when(matchingRequestPaymentRepository.findFirstByMatchingRequestIdAndMatchingOfferIdOrderByIdDesc(10L, 300L))
                .thenReturn(Optional.of(payment(fixture.myRequest(), fixture.offer(), 40_000)));
        when(lessonCancellationRepository.findByLessonIdAndMatchingRequestId(500L, 10L))
                .thenReturn(List.of());
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
        MatchingRequest myRequest = matchingRequest(10L, consumer, 3);
        MatchingRequest otherRequest = matchingRequest(11L, otherConsumer, 2);
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
                lessonParticipant(100L, lesson, myRequest, 38, Gender.MALE),
                lessonParticipant(101L, lesson, myRequest, 12, Gender.FEMALE),
                lessonParticipant(102L, lesson, otherRequest, 9, Gender.MALE)
        );
        return new Fixture(lesson, offer, myRequest, consumer, instructorMember, participants);
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
            LessonStartConfirmationActor actor,
            Instant confirmedAt
    ) {
        LessonStartConfirmation confirmation = construct(LessonStartConfirmation.class);
        ReflectionTestUtils.setField(confirmation, "lesson", lesson);
        ReflectionTestUtils.setField(confirmation, "member", member);
        ReflectionTestUtils.setField(confirmation, "matchingRequest", matchingRequest);
        ReflectionTestUtils.setField(confirmation, "actorType", actor);
        ReflectionTestUtils.setField(confirmation, "status", LessonStartConfirmationStatus.CONFIRMED);
        ReflectionTestUtils.setField(confirmation, "confirmedAt", confirmedAt);
        return confirmation;
    }

    private LessonCancellation cancellation(
            Lesson lesson,
            Member member,
            MatchingRequest matchingRequest,
            LessonCancellationActor actor
    ) {
        return cancellation(
                lesson,
                member,
                matchingRequest,
                actor,
                Instant.parse("2026-07-10T00:30:00Z")
        );
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

    private JsonNode toJson(ConsumerLessonDetailResponse response) throws Exception {
        return OBJECT_MAPPER.valueToTree(response);
    }

    private record Fixture(
            Lesson lesson,
            MatchingOffer offer,
            MatchingRequest myRequest,
            Member consumer,
            Member instructorMember,
            List<LessonParticipant> participants
    ) {
    }
}
