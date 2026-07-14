package org.sopt.ssingserver.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
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
import org.sopt.ssingserver.domain.home.dto.response.InstructorHomeResponse;
import org.sopt.ssingserver.domain.home.enums.InstructorHomeDisplayStatus;
import org.sopt.ssingserver.domain.home.error.HomeErrorCode;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
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
class InstructorHomeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-09T00:00:00Z"),
            ZoneOffset.UTC
    );
    private static final List<LessonStatus> UPCOMING_LESSON_STATUSES = List.of(
            LessonStatus.CONFIRMED,
            LessonStatus.IN_PROGRESS
    );
    private static final List<MatchingRequestStatus> MATCHING_CONSUMER_COUNT_STATUSES = List.of(
            MatchingRequestStatus.REQUESTED,
            MatchingRequestStatus.GROUPED,
            MatchingRequestStatus.MATCHED
    );

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
    void getInstructorHome은_리뷰가_없으면_리뷰_요약을_빈_객체로_반환한다() {
        InstructorHomeService service = createService();
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
        assertThat(response.matchingPeopleCount()).isZero();
        assertThat(response.reviewSummary().averageRating()).isNull();
        assertThat(response.reviewSummary().grade()).isNull();
        assertThat(response.reviewSummary().achievementRate()).isNull();
        assertThat(response.hasUnreadNotification()).isFalse();
    }

    @Test
    void getInstructorHome은_리뷰가_있으면_평균평점_강사레벨_경험치를_반환한다() {
        InstructorHomeService service = createService();
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
    void getInstructorHome은_결제대기_활성협상_1건을_홈_displayStatus로_반환한다() {
        InstructorHomeService service = createService();
        Resort resort = highOneResort();
        MatchingRequest paymentPendingRequest = matchingRequest(resort, 4, "이민수");
        MatchingRequestGroup paymentPendingGroup = matchingRequestGroup(22L, MatchingRequestGroupStatus.PAYMENT_PENDING);
        MatchingOffer paymentPendingOffer = matchingOffer(33L, paymentPendingGroup, Instant.parse("2026-07-09T03:00:00Z"));
        paymentPendingOffer.accept(FIXED_CLOCK.instant());
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
                .thenReturn(List.of(paymentPendingOffer));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
                List.of(22L)
        ))
                .thenReturn(List.of(paymentPendingItem));
        when(lessonRepository.findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                1L,
                UPCOMING_LESSON_STATUSES
        )).thenReturn(List.of());

        InstructorHomeResponse response = service.getInstructorHome(1L);

        assertThat(response.lessonCards())
                .extracting(InstructorHomeResponse.LessonCardResponse::displayStatus)
                .containsExactly(InstructorHomeDisplayStatus.PAYMENT_PENDING.name());
        assertThat(response.lessonCards().getFirst().offerId()).isEqualTo(33L);
        assertThat(response.lessonCards().getFirst().sport()).isSameAs(Sport.SKI);
        assertThat(response.lessonCards().get(0).scheduledAt())
                .isEqualTo(OffsetDateTime.of(2026, 7, 9, 12, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void getInstructorHome은_강사가_즉시노출_중이면_MATCHING_카드로_반환한다() {
        InstructorHomeService service = createService();
        Resort resort = highOneResort();
        InstructorProfile instructorProfile = instructorProfileWithResort(resort);
        InstructorMatchingSetting matchingSetting = exposedMatchingSetting(
                instructorProfile,
                Instant.parse("2026-07-09T00:30:00Z")
        );
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
        when(matchingRequestRepository.sumHeadcountByStatusIn(MATCHING_CONSUMER_COUNT_STATUSES))
                .thenReturn(7L);
        when(instructorMatchingSettingRepository.countByIsExposedTrue())
                .thenReturn(3L);

        InstructorHomeResponse response = service.getInstructorHome(1L);

        InstructorHomeResponse.LessonCardResponse lessonCard = response.lessonCards().get(0);
        assertThat(lessonCard.displayStatus()).isEqualTo(InstructorHomeDisplayStatus.MATCHING.name());
        assertThat(lessonCard.title()).isEqualTo("매칭중");
        assertThat(lessonCard.sport()).isSameAs(Sport.SKI);
        assertThat(lessonCard.scheduledAt())
                .isEqualTo(OffsetDateTime.of(2026, 7, 9, 9, 30, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(response.matchingPeopleCount()).isEqualTo(10L);
    }

    @Test
    void getInstructorHome은_제안_카드가_있으면_즉시노출_MATCHING_카드를_함께_반환하지_않는다() {
        InstructorHomeService service = createService();
        Resort resort = highOneResort();
        MatchingRequest matchingRequest = matchingRequest(resort, 3, "김철수");
        matchingRequest.markGrouped();
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
        InstructorHomeService service = createService();
        Resort resort = highOneResort();
        MatchingRequest matchingRequest = matchingRequest(resort, 1, "김철수");
        MatchingRequestGroup matchingRequestGroup = matchingRequestGroupWithId(20L);
        MatchingOffer matchingOffer = matchingOffer(
                30L,
                matchingRequestGroup,
                Instant.parse("2026-07-09T00:00:00Z")
        );
        MatchingRequestGroupItem groupItem = groupItem(matchingRequest, matchingRequestGroup);
        Lesson lesson = confirmedLesson(
                40L,
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
        assertThat(lessonCard.offerId()).isEqualTo(30L);
        assertThat(lessonCard.displayStatus()).isEqualTo(LessonStatus.CONFIRMED.name());
        assertThat(lessonCard.remainingDays()).isEqualTo(3);
        assertThat(lessonCard.title()).isEqualTo("김철수님 팀 4명");
        assertThat(lessonCard.sport()).isSameAs(Sport.SKI);
        assertThat(lessonCard.scheduledAt())
                .isEqualTo(OffsetDateTime.of(2026, 7, 12, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(lessonCard.resort().code()).isEqualTo("HIGH1");
        assertThat(lessonCard.resort().displayName()).isEqualTo("하이원");
    }

    @Test
    void getInstructorHome은_여러_강습을_offerId로_구분하고_진행중_강습에도_두_ID를_반환한다() {
        InstructorHomeService service = createService();
        Resort resort = highOneResort();
        MatchingRequestGroup confirmedGroup = matchingRequestGroupWithId(20L);
        MatchingRequestGroup inProgressGroup = matchingRequestGroupWithId(21L);
        MatchingOffer confirmedOffer = matchingOffer(
                30L,
                confirmedGroup,
                Instant.parse("2026-07-09T00:00:00Z")
        );
        MatchingOffer inProgressOffer = matchingOffer(
                31L,
                inProgressGroup,
                Instant.parse("2026-07-09T00:10:00Z")
        );
        Lesson confirmedLesson = confirmedLesson(
                40L,
                Instant.parse("2026-07-12T01:00:00Z"),
                2,
                resort,
                confirmedOffer
        );
        Lesson inProgressLesson = confirmedLesson(
                41L,
                Instant.parse("2026-07-09T00:30:00Z"),
                3,
                resort,
                inProgressOffer
        );
        inProgressLesson.start(FIXED_CLOCK.instant());

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
        )).thenReturn(List.of(confirmedLesson, inProgressLesson));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
                List.of(20L, 21L)
        )).thenReturn(List.of(
                groupItem(matchingRequest(resort, 2, "김철수"), confirmedGroup),
                groupItem(matchingRequest(resort, 3, "이민수"), inProgressGroup)
        ));

        InstructorHomeResponse response = service.getInstructorHome(1L);

        assertThat(response.lessonCards()).hasSize(2);
        assertThat(response.lessonCards().get(0).offerId()).isEqualTo(30L);
        assertThat(response.lessonCards().get(0).lessonId()).isEqualTo(40L);
        assertThat(response.lessonCards().get(0).displayStatus()).isEqualTo(LessonStatus.CONFIRMED.name());
        assertThat(response.lessonCards().get(1).offerId()).isEqualTo(31L);
        assertThat(response.lessonCards().get(1).lessonId()).isEqualTo(41L);
        assertThat(response.lessonCards().get(1).displayStatus()).isEqualTo(LessonStatus.IN_PROGRESS.name());
    }

    @Test
    void getInstructorHome은_그룹_item이_없으면_명확한_홈_예외를_던진다() {
        InstructorHomeService service = createService();
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

    private InstructorHomeService createService() {
        return new InstructorHomeService(
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

    private Resort highOneResort() {
        return Resort.create("HIGH1", "하이원 리조트", "하이원", 0);
    }

    private MatchingRequest matchingRequest(
            Resort resort,
            int headcount,
            String requesterNickname
    ) {
        Member member = Member.create(
                requesterNickname,
                null,
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE
        );
        return MatchingRequest.create(
                member,
                resort,
                Sport.SKI,
                LessonLevel.BEGINNER,
                headcount,
                List.of(60),
                true
        );
    }

    private MatchingRequestGroup matchingRequestGroup(
            Long id,
            MatchingRequestGroupStatus status
    ) {
        MatchingRequestGroup matchingRequestGroup = MatchingRequestGroup.createCandidate(60);
        ReflectionTestUtils.setField(matchingRequestGroup, "id", id);
        switch (status) {
            case CANDIDATE -> {
            }
            case EXPOSED -> matchingRequestGroup.expose();
            case INSTRUCTOR_ACCEPTED -> matchingRequestGroup.markInstructorAccepted();
            case PAYMENT_PENDING -> matchingRequestGroup.markPaymentPending();
            default -> throw new IllegalArgumentException("Unsupported home fixture group status: " + status);
        }
        return matchingRequestGroup;
    }

    private MatchingRequestGroup matchingRequestGroupWithId(Long id) {
        return matchingRequestGroup(id, MatchingRequestGroupStatus.CANDIDATE);
    }

    private MatchingOffer matchingOffer(
            Long id,
            MatchingRequestGroup matchingRequestGroup,
            Instant exposedAt
    ) {
        MatchingOffer matchingOffer = MatchingOffer.create(
                instructorProfile(),
                matchingRequestGroup,
                exposedAt
        );
        ReflectionTestUtils.setField(matchingOffer, "id", id);
        return matchingOffer;
    }

    private MatchingOffer matchingOfferWithGroup(MatchingRequestGroup matchingRequestGroup) {
        return MatchingOffer.create(
                instructorProfile(),
                matchingRequestGroup,
                Instant.parse("2026-07-09T00:00:00Z")
        );
    }

    private Lesson confirmedLesson(
            Long id,
            Instant scheduledAt,
            int totalHeadcount,
            Resort resort,
            MatchingOffer matchingOffer
    ) {
        Lesson lesson = Lesson.createImmediateConfirmed(
                instructorProfile(),
                resort,
                matchingOffer,
                Sport.SKI,
                LessonLevel.BEGINNER,
                totalHeadcount,
                60,
                scheduledAt
        );
        ReflectionTestUtils.setField(lesson, "id", id);
        return lesson;
    }

    private MatchingRequestGroupItem groupItem(
            MatchingRequest matchingRequest,
            MatchingRequestGroup matchingRequestGroup
    ) {
        return MatchingRequestGroupItem.createNotRequested(matchingRequest, matchingRequestGroup);
    }

    private InstructorMatchingSetting exposedMatchingSetting(
            InstructorProfile instructorProfile,
            Instant updatedAt
    ) {
        InstructorMatchingSetting setting = InstructorMatchingSetting.create(
                instructorProfile,
                Sport.SKI,
                List.of(LessonLevel.BEGINNER),
                List.of(60),
                5,
                true
        );
        ReflectionTestUtils.setField(setting, "updatedAt", updatedAt);
        return setting;
    }

    private void stubReviewSummary(Double averageRating) {
        when(reviewRepository.findAverageRatingByInstructorProfileId(1L)).thenReturn(averageRating);
    }
}
