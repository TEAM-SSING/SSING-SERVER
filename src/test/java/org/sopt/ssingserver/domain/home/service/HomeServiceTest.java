package org.sopt.ssingserver.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse.LessonCardResponse;
import org.sopt.ssingserver.domain.home.dto.response.InstructorHomeResponse;
import org.sopt.ssingserver.domain.home.enums.InstructorHomeDisplayStatus;
import org.sopt.ssingserver.domain.home.error.HomeErrorCode;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.lesson.repository.projection.HomeLessonCardProjection;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.matching.service.MatchingStatusResolver;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.domain.review.repository.ReviewRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-09T00:00:00Z"),
            ZoneOffset.UTC
    );
    private static final List<LessonStatus> UPCOMING_LESSON_STATUSES = List.of(
            LessonStatus.CONFIRMED,
            LessonStatus.IN_PROGRESS
    );

    @Mock
    private LessonParticipantRepository lessonParticipantRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private InstructorMatchingSettingRepository instructorMatchingSettingRepository;

    @Mock
    private InstructorProfileRepository instructorProfileRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Test
    void getConsumerHome은_예정된_강습을_D_day와_함께_반환한다() {
        HomeService service = createService();
        HomeLessonCardProjection lessonCard = lessonCard(
                1L,
                LessonStatus.CONFIRMED,
                Instant.parse("2026-07-11T10:00:00Z"),
                4,
                "김철수"
        );
        when(lessonParticipantRepository.findHomeLessonCardsByMemberIdAndLessonStatusIn(1L, UPCOMING_LESSON_STATUSES))
                .thenReturn(List.of(lessonCard));

        ConsumerHomeResponse response = service.getConsumerHome(1L);

        assertThat(response.hasUnreadNotification()).isFalse();
        assertThat(response.lessonCards()).hasSize(1);
        LessonCardResponse lesson = response.lessonCards().get(0);
        assertThat(lesson.lessonId()).isEqualTo(1L);
        assertThat(lesson.displayStatus()).isSameAs(LessonStatus.CONFIRMED);
        assertThat(lesson.remainingDays()).isEqualTo(2);
        assertThat(lesson.title()).isEqualTo("김철수님 팀 4명");
        assertThat(lesson.scheduledAt()).isEqualTo(OffsetDateTime.of(2026, 7, 11, 19, 0, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(lesson.resort().code()).isEqualTo("HIGH1");
        assertThat(lesson.resort().displayName()).isEqualTo("하이원");
    }

    @Test
    void getConsumerHome은_진행중인_강습의_remainingDays를_0으로_고정한다() {
        HomeService service = createService();
        HomeLessonCardProjection lessonCard = lessonCard(
                2L,
                LessonStatus.IN_PROGRESS,
                Instant.parse("2026-07-09T05:00:00Z"),
                2,
                "박영희"
        );
        when(lessonParticipantRepository.findHomeLessonCardsByMemberIdAndLessonStatusIn(1L, UPCOMING_LESSON_STATUSES))
                .thenReturn(List.of(lessonCard));

        ConsumerHomeResponse response = service.getConsumerHome(1L);

        LessonCardResponse lesson = response.lessonCards().get(0);
        assertThat(lesson.displayStatus()).isSameAs(LessonStatus.IN_PROGRESS);
        assertThat(lesson.remainingDays()).isZero();
        assertThat(lesson.title()).isEqualTo("박영희님 팀 2명");
    }

    @Test
    void getConsumerHome은_조회된_강습_카드를_순서대로_반환한다() {
        HomeService service = createService();
        HomeLessonCardProjection firstLessonCard = lessonCard(
                1L,
                LessonStatus.CONFIRMED,
                Instant.parse("2026-07-11T10:00:00Z"),
                4,
                "김철수"
        );
        HomeLessonCardProjection secondLessonCard = lessonCard(
                2L,
                LessonStatus.CONFIRMED,
                Instant.parse("2026-07-12T10:00:00Z"),
                1,
                "박영희"
        );
        when(lessonParticipantRepository.findHomeLessonCardsByMemberIdAndLessonStatusIn(1L, UPCOMING_LESSON_STATUSES))
                .thenReturn(List.of(firstLessonCard, secondLessonCard));

        ConsumerHomeResponse response = service.getConsumerHome(1L);

        assertThat(response.lessonCards())
                .extracting(LessonCardResponse::lessonId)
                .containsExactly(1L, 2L);
    }

    @Test
    void getConsumerHome은_예약된_강습이_없으면_빈_배열을_반환한다() {
        HomeService service = createService();
        when(lessonParticipantRepository.findHomeLessonCardsByMemberIdAndLessonStatusIn(1L, UPCOMING_LESSON_STATUSES))
                .thenReturn(List.of());

        ConsumerHomeResponse response = service.getConsumerHome(1L);

        assertThat(response.lessonCards()).isEmpty();
        assertThat(response.hasUnreadNotification()).isFalse();
    }

    @Test
    void getInstructorHome은_리뷰가_없으면_리뷰_요약을_빈_객체로_반환한다() {
        HomeService service = createService();
        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(instructorProfile()));
        stubReviewSummary(null);
        when(matchingOfferRepository.findInstructorHomeOffers(
                1L,
                MatchingOfferStatus.OFFERED,
                MatchingOfferStatus.ACCEPTED,
                List.of(
                        MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                        MatchingRequestGroupStatus.PAYMENT_PENDING
                )
        ))
                .thenReturn(List.of());
        when(lessonRepository.findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                1L,
                UPCOMING_LESSON_STATUSES
        )).thenReturn(List.of());

        InstructorHomeResponse response = service.getInstructorHome(1L);

        assertThat(response.lessonCards()).isEmpty();
        assertThat(response.matchingConsumerCount()).isZero();
        assertThat(response.reviewSummary().averageRating()).isNull();
        assertThat(response.reviewSummary().grade()).isNull();
        assertThat(response.reviewSummary().achievementRate()).isNull();
        assertThat(response.hasUnreadNotification()).isFalse();
    }

    @Test
    void getInstructorHome은_리뷰가_있으면_평균평점_강사레벨_경험치를_반환한다() {
        HomeService service = createService();
        InstructorProfile instructorProfile = instructorProfile(4, 88);
        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(instructorProfile));
        stubReviewSummary(3.25);
        when(matchingOfferRepository.findInstructorHomeOffers(
                1L,
                MatchingOfferStatus.OFFERED,
                MatchingOfferStatus.ACCEPTED,
                List.of(
                        MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                        MatchingRequestGroupStatus.PAYMENT_PENDING
                )
        ))
                .thenReturn(List.of());
        when(lessonRepository.findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                1L,
                UPCOMING_LESSON_STATUSES
        )).thenReturn(List.of());

        InstructorHomeResponse response = service.getInstructorHome(1L);

        assertThat(response.reviewSummary().averageRating()).isEqualTo(3.3);
        assertThat(response.reviewSummary().grade()).isEqualTo(4);
        assertThat(response.reviewSummary().achievementRate()).isEqualTo(88);
    }

    @Test
    void getInstructorHome은_강사_제안_상태를_홈_displayStatus로_변환한다() {
        HomeService service = createService();
        Resort resort = resortDisplayOnly();
        MatchingRequest offeredRequest = matchingRequest(10L, MatchingRequestStatus.GROUPED, resort, 3, "김철수");
        MatchingRequest acceptedRequest = matchingRequest(11L, MatchingRequestStatus.MATCHED, resort, 2, "박영희");
        MatchingRequest paymentPendingRequest = matchingRequest(12L, null, resort, 4, "이민수");
        MatchingRequestGroup offeredGroup = matchingRequestGroup(20L, MatchingRequestGroupStatus.EXPOSED);
        MatchingRequestGroup acceptedGroup = matchingRequestGroup(21L, MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED);
        MatchingRequestGroup paymentPendingGroup = matchingRequestGroup(22L, MatchingRequestGroupStatus.PAYMENT_PENDING);
        MatchingOffer offeredOffer = matchingOffer(31L, offeredGroup, Instant.parse("2026-07-09T01:00:00Z"));
        MatchingOffer acceptedOffer = matchingOffer(32L, acceptedGroup, Instant.parse("2026-07-09T02:00:00Z"));
        MatchingOffer paymentPendingOffer = matchingOffer(33L, paymentPendingGroup, Instant.parse("2026-07-09T03:00:00Z"));
        MatchingRequestGroupItem offeredItem = groupItem(offeredRequest, offeredGroup);
        MatchingRequestGroupItem acceptedItem = groupItem(acceptedRequest, acceptedGroup);
        MatchingRequestGroupItem paymentPendingItem = groupItem(paymentPendingRequest, paymentPendingGroup);
        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(instructorProfile()));
        stubReviewSummary(null);
        when(matchingOfferRepository.findInstructorHomeOffers(
                1L,
                MatchingOfferStatus.OFFERED,
                MatchingOfferStatus.ACCEPTED,
                List.of(
                        MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                        MatchingRequestGroupStatus.PAYMENT_PENDING
                )
        ))
                .thenReturn(List.of(offeredOffer, acceptedOffer, paymentPendingOffer));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
                List.of(20L, 21L, 22L)
        ))
                .thenReturn(List.of(offeredItem, acceptedItem, paymentPendingItem));
        when(lessonRepository.findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                1L,
                UPCOMING_LESSON_STATUSES
        )).thenReturn(List.of());

        InstructorHomeResponse response = service.getInstructorHome(1L);

        assertThat(response.lessonCards())
                .extracting(InstructorHomeResponse.LessonCardResponse::displayStatus)
                .containsExactly(
                        InstructorHomeDisplayStatus.WAITING_FOR_INSTRUCTOR.name(),
                        InstructorHomeDisplayStatus.WAITING_FOR_CONFIRMATION.name(),
                        InstructorHomeDisplayStatus.PAYMENT_PENDING.name()
        );
        assertThat(response.lessonCards().get(0).scheduledAt())
                .isEqualTo(OffsetDateTime.of(2026, 7, 9, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void getInstructorHome은_강사가_즉시노출_중이면_MATCHING_카드로_반환한다() {
        HomeService service = createService();
        Resort resort = resortWithDetails();
        InstructorProfile instructorProfile = instructorProfileWithResort(resort);
        InstructorMatchingSetting matchingSetting = instructorMatchingSetting(true, Instant.parse("2026-07-09T00:30:00Z"));
        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(instructorProfile));
        stubReviewSummary(null);
        when(instructorMatchingSettingRepository.findByInstructorProfileId(1L))
                .thenReturn(Optional.of(matchingSetting));
        when(matchingOfferRepository.findInstructorHomeOffers(
                1L,
                MatchingOfferStatus.OFFERED,
                MatchingOfferStatus.ACCEPTED,
                List.of(
                        MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                        MatchingRequestGroupStatus.PAYMENT_PENDING
                )
        ))
                .thenReturn(List.of());
        when(lessonRepository.findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                1L,
                UPCOMING_LESSON_STATUSES
        )).thenReturn(List.of());
        when(matchingRequestRepository.sumHeadcountByResortIdAndStatus(100L, MatchingRequestStatus.REQUESTED))
                .thenReturn(3L);

        InstructorHomeResponse response = service.getInstructorHome(1L);

        InstructorHomeResponse.LessonCardResponse lessonCard = response.lessonCards().get(0);
        assertThat(lessonCard.displayStatus()).isEqualTo(InstructorHomeDisplayStatus.MATCHING.name());
        assertThat(lessonCard.title()).isEqualTo("매칭중");
        assertThat(lessonCard.scheduledAt())
                .isEqualTo(OffsetDateTime.of(2026, 7, 9, 9, 30, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(response.matchingConsumerCount()).isEqualTo(3L);
    }

    @Test
    void getInstructorHome은_제안_카드가_있으면_즉시노출_MATCHING_카드를_함께_반환하지_않는다() {
        HomeService service = createService();
        Resort resort = resortDisplayOnly();
        MatchingRequest matchingRequest = matchingRequest(10L, MatchingRequestStatus.GROUPED, resort, 3, "김철수");
        MatchingRequestGroup matchingRequestGroup = matchingRequestGroup(20L, MatchingRequestGroupStatus.EXPOSED);
        MatchingOffer matchingOffer = matchingOffer(30L, matchingRequestGroup, Instant.parse("2026-07-09T01:00:00Z"));
        MatchingRequestGroupItem groupItem = groupItem(matchingRequest, matchingRequestGroup);
        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(instructorProfile()));
        stubReviewSummary(null);
        when(matchingOfferRepository.findInstructorHomeOffers(
                1L,
                MatchingOfferStatus.OFFERED,
                MatchingOfferStatus.ACCEPTED,
                List.of(
                        MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                        MatchingRequestGroupStatus.PAYMENT_PENDING
                )
        ))
                .thenReturn(List.of(matchingOffer));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
                List.of(20L)
        ))
                .thenReturn(List.of(groupItem));
        when(lessonRepository.findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                1L,
                UPCOMING_LESSON_STATUSES
        )).thenReturn(List.of());

        InstructorHomeResponse response = service.getInstructorHome(1L);

        assertThat(response.lessonCards())
                .extracting(InstructorHomeResponse.LessonCardResponse::displayStatus)
                .containsExactly(InstructorHomeDisplayStatus.WAITING_FOR_INSTRUCTOR.name());
        verify(instructorMatchingSettingRepository, never()).findByInstructorProfileId(1L);
    }

    @Test
    void getInstructorHome은_강사에게_배정된_확정_강습을_카드로_반환한다() {
        HomeService service = createService();
        Resort resort = resortDisplayOnly();
        MatchingRequest matchingRequest = matchingRequestWithRequesterNickname("김철수");
        MatchingRequestGroup matchingRequestGroup = matchingRequestGroupWithId(20L);
        MatchingOffer matchingOffer = matchingOfferWithGroup(matchingRequestGroup);
        MatchingRequestGroupItem groupItem = groupItem(matchingRequest, matchingRequestGroup);
        Lesson lesson = lesson(
                40L,
                LessonStatus.CONFIRMED,
                Instant.parse("2026-07-12T01:00:00Z"),
                4,
                resort,
                matchingOffer
        );
        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(instructorProfile()));
        stubReviewSummary(null);
        when(matchingOfferRepository.findInstructorHomeOffers(
                1L,
                MatchingOfferStatus.OFFERED,
                MatchingOfferStatus.ACCEPTED,
                List.of(
                        MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                        MatchingRequestGroupStatus.PAYMENT_PENDING
                )
        ))
                .thenReturn(List.of());
        when(lessonRepository.findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                1L,
                UPCOMING_LESSON_STATUSES
        )).thenReturn(List.of(lesson));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
                List.of(20L)
        ))
                .thenReturn(List.of(groupItem));

        InstructorHomeResponse response = service.getInstructorHome(1L);

        InstructorHomeResponse.LessonCardResponse lessonCard = response.lessonCards().get(0);
        assertThat(lessonCard.lessonId()).isEqualTo(40L);
        assertThat(lessonCard.offerId()).isNull();
        assertThat(lessonCard.displayStatus()).isEqualTo(LessonStatus.CONFIRMED.name());
        assertThat(lessonCard.remainingDays()).isEqualTo(3);
        assertThat(lessonCard.title()).isEqualTo("김철수님 팀 4명");
        assertThat(lessonCard.scheduledAt())
                .isEqualTo(OffsetDateTime.of(2026, 7, 12, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(lessonCard.resort().code()).isEqualTo("HIGH1");
        assertThat(lessonCard.resort().displayName()).isEqualTo("하이원");
    }

    @Test
    void getInstructorHome은_그룹_item이_없으면_명확한_홈_예외를_던진다() {
        HomeService service = createService();
        MatchingRequestGroup matchingRequestGroup = matchingRequestGroupWithId(20L);
        MatchingOffer matchingOffer = matchingOfferWithGroup(matchingRequestGroup);
        when(instructorProfileRepository.findByMemberId(1L))
                .thenReturn(Optional.of(instructorProfile()));
        when(matchingOfferRepository.findInstructorHomeOffers(
                1L,
                MatchingOfferStatus.OFFERED,
                MatchingOfferStatus.ACCEPTED,
                List.of(
                        MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                        MatchingRequestGroupStatus.PAYMENT_PENDING
                )
        ))
                .thenReturn(List.of(matchingOffer));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
                List.of(20L)
        ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getInstructorHome(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isSameAs(HomeErrorCode.INSTRUCTOR_HOME_GROUP_ITEM_NOT_FOUND));
    }

    private HomeService createService() {
        return new HomeService(
                lessonParticipantRepository,
                lessonRepository,
                matchingOfferRepository,
                matchingRequestGroupItemRepository,
                matchingRequestRepository,
                instructorMatchingSettingRepository,
                instructorProfileRepository,
                reviewRepository,
                new MatchingStatusResolver(),
                FIXED_CLOCK
        );
    }

    private InstructorProfile instructorProfile() {
        return instructorProfile(1, 0);
    }

    private InstructorProfile instructorProfile(
            int level,
            int experience
    ) {
        Member member = Member.create("강사", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE);
        InstructorProfile instructorProfile = InstructorProfile.create(
                member,
                "강사",
                "01012345678",
                Gender.FEMALE,
                java.time.LocalDate.of(1998, 1, 1),
                null,
                java.time.LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(instructorProfile, "id", 1L);
        ReflectionTestUtils.setField(instructorProfile, "level", level);
        ReflectionTestUtils.setField(instructorProfile, "experience", experience);
        return instructorProfile;
    }

    private InstructorProfile instructorProfileWithResort(Resort resort) {
        InstructorProfile instructorProfile = instructorProfile();
        ReflectionTestUtils.setField(instructorProfile, "resort", resort);
        return instructorProfile;
    }

    private Resort resortWithDetails() {
        Resort resort = mock(Resort.class);
        when(resort.getId()).thenReturn(100L);
        when(resort.getCode()).thenReturn("HIGH1");
        when(resort.getDisplayName()).thenReturn("하이원");
        return resort;
    }

    private Resort resortDisplayOnly() {
        Resort resort = mock(Resort.class);
        when(resort.getCode()).thenReturn("HIGH1");
        when(resort.getDisplayName()).thenReturn("하이원");
        return resort;
    }

    private MatchingRequest matchingRequest(
            Long id,
            MatchingRequestStatus status,
            Resort resort,
            int headcount,
            String requesterNickname
    ) {
        MatchingRequest matchingRequest = matchingRequest(id, status, resort, headcount, (Instant) null);
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn(requesterNickname);
        when(matchingRequest.getMember()).thenReturn(member);
        return matchingRequest;
    }

    private MatchingRequest matchingRequestWithRequesterNickname(String requesterNickname) {
        MatchingRequest matchingRequest = mock(MatchingRequest.class);
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn(requesterNickname);
        when(matchingRequest.getMember()).thenReturn(member);
        return matchingRequest;
    }

    private MatchingRequest matchingRequest(
            Long id,
            MatchingRequestStatus status,
            Resort resort,
            int headcount,
            Instant updatedAt
    ) {
        MatchingRequest matchingRequest = mock(MatchingRequest.class);
        when(matchingRequest.getId()).thenReturn(id);
        if (status != null) {
            when(matchingRequest.getStatus()).thenReturn(status);
        }
        when(matchingRequest.getResort()).thenReturn(resort);
        if (headcount > 0) {
            when(matchingRequest.getHeadcount()).thenReturn(headcount);
        }
        if (updatedAt != null) {
            when(matchingRequest.getUpdatedAt()).thenReturn(updatedAt);
        }
        return matchingRequest;
    }

    private MatchingRequestGroup matchingRequestGroup(
            Long id,
            MatchingRequestGroupStatus status
    ) {
        MatchingRequestGroup matchingRequestGroup = mock(MatchingRequestGroup.class);
        when(matchingRequestGroup.getId()).thenReturn(id);
        when(matchingRequestGroup.getStatus()).thenReturn(status);
        return matchingRequestGroup;
    }

    private MatchingRequestGroup matchingRequestGroupWithId(Long id) {
        MatchingRequestGroup matchingRequestGroup = mock(MatchingRequestGroup.class);
        when(matchingRequestGroup.getId()).thenReturn(id);
        return matchingRequestGroup;
    }

    private MatchingOffer matchingOffer(
            Long id,
            MatchingRequestGroup matchingRequestGroup,
            Instant exposedAt
    ) {
        MatchingOffer matchingOffer = mock(MatchingOffer.class);
        when(matchingOffer.getId()).thenReturn(id);
        when(matchingOffer.getMatchingRequestGroup()).thenReturn(matchingRequestGroup);
        when(matchingOffer.getExposedAt()).thenReturn(exposedAt);
        return matchingOffer;
    }

    private MatchingOffer matchingOfferWithGroup(MatchingRequestGroup matchingRequestGroup) {
        MatchingOffer matchingOffer = mock(MatchingOffer.class);
        when(matchingOffer.getMatchingRequestGroup()).thenReturn(matchingRequestGroup);
        return matchingOffer;
    }

    private Lesson lesson(
            Long id,
            LessonStatus status,
            Instant scheduledAt,
            int totalHeadcount,
            Resort resort,
            MatchingOffer matchingOffer
    ) {
        Lesson lesson = mock(Lesson.class);
        when(lesson.getId()).thenReturn(id);
        when(lesson.getStatus()).thenReturn(status);
        when(lesson.getScheduledAt()).thenReturn(scheduledAt);
        when(lesson.getTotalHeadcount()).thenReturn(totalHeadcount);
        when(lesson.getResort()).thenReturn(resort);
        when(lesson.getMatchingOffer()).thenReturn(matchingOffer);
        return lesson;
    }

    private MatchingRequestGroupItem groupItem(
            MatchingRequest matchingRequest,
            MatchingRequestGroup matchingRequestGroup
    ) {
        MatchingRequestGroupItem groupItem = mock(MatchingRequestGroupItem.class);
        when(groupItem.getMatchingRequest()).thenReturn(matchingRequest);
        when(groupItem.getMatchingRequestGroup()).thenReturn(matchingRequestGroup);
        return groupItem;
    }

    private InstructorMatchingSetting instructorMatchingSetting(
            boolean isExposed,
            Instant updatedAt
    ) {
        InstructorMatchingSetting setting = mock(InstructorMatchingSetting.class);
        when(setting.isExposed()).thenReturn(isExposed);
        when(setting.getUpdatedAt()).thenReturn(updatedAt);
        return setting;
    }

    private void stubReviewSummary(Double averageRating) {
        when(reviewRepository.findAverageRatingByInstructorProfileId(1L)).thenReturn(averageRating);
    }

    private HomeLessonCardProjection lessonCard(
            Long lessonId,
            LessonStatus lessonStatus,
            Instant scheduledAt,
            int totalHeadcount,
            String requesterNickname
    ) {
        return new TestHomeLessonCardProjection(
                lessonId,
                lessonStatus,
                scheduledAt,
                requesterNickname,
                totalHeadcount,
                "HIGH1",
                "하이원"
        );
    }

    private record TestHomeLessonCardProjection(
            Long lessonId,
            LessonStatus lessonStatus,
            Instant scheduledAt,
            String requesterNickname,
            int totalHeadcount,
            String resortCode,
            String resortDisplayName
    ) implements HomeLessonCardProjection {

        @Override
        public Long getLessonId() {
            return lessonId;
        }

        @Override
        public LessonStatus getLessonStatus() {
            return lessonStatus;
        }

        @Override
        public Instant getScheduledAt() {
            return scheduledAt;
        }

        @Override
        public String getRequesterNickname() {
            return requesterNickname;
        }

        @Override
        public int getTotalHeadcount() {
            return totalHeadcount;
        }

        @Override
        public String getResortCode() {
            return resortCode;
        }

        @Override
        public String getResortDisplayName() {
            return resortDisplayName;
        }
    }
}
