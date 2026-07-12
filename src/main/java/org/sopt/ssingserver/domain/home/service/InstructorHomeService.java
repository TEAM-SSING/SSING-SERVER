package org.sopt.ssingserver.domain.home.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.home.dto.response.InstructorHomeResponse;
import org.sopt.ssingserver.domain.home.enums.InstructorHomeDisplayStatus;
import org.sopt.ssingserver.domain.home.error.HomeErrorCode;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
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
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.matching.service.MatchingStatusResolver;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.domain.review.repository.ReviewRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.time.AppZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstructorHomeService {

    private static final List<LessonStatus> UPCOMING_LESSON_STATUSES = List.of(
            LessonStatus.CONFIRMED,
            LessonStatus.IN_PROGRESS
    );
    private static final List<MatchingRequestGroupStatus> INSTRUCTOR_HOME_ACCEPTED_GROUP_STATUSES = List.of(
            MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
            MatchingRequestGroupStatus.PAYMENT_PENDING
    );
    private static final List<MatchingRequestStatus> MATCHING_CONSUMER_COUNT_STATUSES = List.of(
            MatchingRequestStatus.REQUESTED,
            MatchingRequestStatus.GROUPED,
            MatchingRequestStatus.MATCHED
    );

    private final LessonRepository lessonRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final InstructorMatchingSettingRepository instructorMatchingSettingRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final ReviewRepository reviewRepository;
    private final MatchingStatusResolver matchingStatusResolver;
    private final Clock clock;

    // 강사 홈에 표시할 매칭중, 제안, 예약/진행 강습 카드 구성함
    @Transactional(readOnly = true)
    public InstructorHomeResponse getInstructorHome(Long memberId) {
        InstructorProfile instructorProfile = instructorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        Instant now = clock.instant();
        List<MatchingOffer> matchingOffers = matchingOfferRepository
                .findInstructorHomeOffers(
                        instructorProfile.getId(),
                        MatchingOfferStatus.OFFERED,
                        MatchingOfferStatus.ACCEPTED,
                        INSTRUCTOR_HOME_ACCEPTED_GROUP_STATUSES
                );
        Map<Long, List<MatchingRequestGroupItem>> offerGroupItemsByGroupId = findGroupItemsByGroupId(
                matchingOffers.stream()
                        .map(MatchingOffer::getMatchingRequestGroup)
                        .map(MatchingRequestGroup::getId)
                        .toList()
        );
        List<InstructorHomeResponse.LessonCardResponse> offerCards = matchingOffers
                .stream()
                .map(matchingOffer -> resolveOfferCard(
                        matchingOffer,
                        getGroupItems(offerGroupItemsByGroupId, matchingOffer.getMatchingRequestGroup())
                ))
                .toList();

        List<InstructorHomeResponse.LessonCardResponse> homeCards = new ArrayList<>();

        // 홈 노출 대상 제안/진행 카드가 없을 때만 강사 노출 설정 기반 MATCHING 카드 확인함
        if (offerCards.isEmpty()) {
            resolveMatchingCard(instructorProfile).ifPresent(homeCards::add);
        }

        homeCards.addAll(offerCards);
        List<Lesson> lessons = lessonRepository
                .findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
                        instructorProfile.getId(),
                        UPCOMING_LESSON_STATUSES
                );
        Map<Long, List<MatchingRequestGroupItem>> lessonGroupItemsByGroupId = findGroupItemsByGroupId(
                lessons.stream()
                        .map(Lesson::getMatchingOffer)
                        .map(MatchingOffer::getMatchingRequestGroup)
                        .map(MatchingRequestGroup::getId)
                        .toList()
        );
        lessons
                .stream()
                .map(lesson -> resolveLessonCard(
                        lesson,
                        now,
                        getGroupItems(lessonGroupItemsByGroupId, lesson.getMatchingOffer().getMatchingRequestGroup())
                ))
                .forEach(homeCards::add);

        // TODO: 알림 읽음 여부 정책 확정 후 실제 조회로 교체함
        boolean hasUnreadNotification = false;
        long matchingConsumerCount = matchingRequestRepository.countByStatusIn(MATCHING_CONSUMER_COUNT_STATUSES);

        return InstructorHomeResponse.from(
                homeCards,
                matchingConsumerCount,
                resolveReviewSummary(instructorProfile),
                hasUnreadNotification
        );
    }

    // 리뷰가 하나 이상 있을 때만 평균 평점/강사 레벨/경험치를 내려줌
    private InstructorHomeResponse.ReviewSummaryResponse resolveReviewSummary(InstructorProfile instructorProfile) {
        Double averageRating = reviewRepository.findAverageRatingByInstructorProfileId(
                instructorProfile.getId()
        );
        if (averageRating == null) {
            return InstructorHomeResponse.ReviewSummaryResponse.empty();
        }

        return InstructorHomeResponse.ReviewSummaryResponse.from(
                roundToOneDecimal(averageRating),
                instructorProfile.getLevel(),
                instructorProfile.getExperience()
        );
    }

    // 평균 평점을 명세의 소수점 한 자리 형태로 정규화함
    private double roundToOneDecimal(Double averageRating) {
        return Math.round(averageRating * 10.0) / 10.0;
    }

    // 강사 노출 ON 상태를 홈 전용 MATCHING 카드로 변환함
    private Optional<InstructorHomeResponse.LessonCardResponse> resolveMatchingCard(InstructorProfile instructorProfile) {
        Resort resort = instructorProfile.getResort();
        if (resort == null) {
            return Optional.empty();
        }

        return instructorMatchingSettingRepository.findByInstructorProfileId(instructorProfile.getId())
                .filter(InstructorMatchingSetting::isExposed)
                .map(setting -> InstructorHomeResponse.LessonCardResponse.from(
                        null,
                        null,
                        0,
                        InstructorHomeDisplayStatus.MATCHING.name(),
                        "매칭중",
                        setting.getUpdatedAt(),
                        resort.getCode(),
                        resort.getDisplayName()
                ));
    }

    // 강사에게 노출된 제안 또는 수락 후 진행 중인 매칭을 홈 카드로 변환함
    private InstructorHomeResponse.LessonCardResponse resolveOfferCard(
            MatchingOffer matchingOffer,
            List<MatchingRequestGroupItem> groupItems
    ) {
        MatchingRequestGroup matchingRequestGroup = matchingOffer.getMatchingRequestGroup();
        MatchingRequestGroupItem representativeItem = findRepresentativeItem(groupItems);
        MatchingRequest representativeRequest = representativeItem.getMatchingRequest();
        String title = resolveTitle(representativeItem, sumHeadcount(groupItems));

        InstructorHomeDisplayStatus displayStatus = resolveOfferDisplayStatus(
                matchingRequestGroup,
                representativeRequest,
                matchingOffer
        );

        return InstructorHomeResponse.LessonCardResponse.from(
                matchingOffer.getId(),
                null,
                0,
                displayStatus.name(),
                title,
                matchingOffer.getExposedAt(),
                representativeRequest.getResort().getCode(),
                representativeRequest.getResort().getDisplayName()
        );
    }

    private InstructorHomeDisplayStatus resolveOfferDisplayStatus(
            MatchingRequestGroup matchingRequestGroup,
            MatchingRequest representativeRequest,
            MatchingOffer matchingOffer
    ) {
        if (matchingRequestGroup.getStatus() == MatchingRequestGroupStatus.PAYMENT_PENDING) {
            return InstructorHomeDisplayStatus.PAYMENT_PENDING;
        }

        // 소비자 matchingStatus 계산기를 재사용하되 강사 홈 offer 카드에서 허용하는 상태만 변환함
        return toInstructorHomeOfferDisplayStatus(matchingStatusResolver.resolve(
                representativeRequest,
                Optional.of(matchingRequestGroup),
                Optional.empty(),
                Optional.of(matchingOffer),
                Optional.empty()
        ));
    }

    // 소비자 매칭 상태 중 강사 홈 offer 카드에서 실제 표시하는 상태만 허용함
    private InstructorHomeDisplayStatus toInstructorHomeOfferDisplayStatus(MatchingStatus matchingStatus) {
        return switch (matchingStatus) {
            case WAITING_FOR_INSTRUCTOR -> InstructorHomeDisplayStatus.WAITING_FOR_INSTRUCTOR;
            case WAITING_FOR_CONFIRMATION -> InstructorHomeDisplayStatus.WAITING_FOR_CONFIRMATION;
            case PAYMENT_PENDING -> InstructorHomeDisplayStatus.PAYMENT_PENDING;
            default -> throw new BusinessException(HomeErrorCode.INSTRUCTOR_HOME_UNSUPPORTED_DISPLAY_STATUS);
        };
    }

    // 강사에게 배정된 확정/진행 강습을 홈 카드로 변환함
    private InstructorHomeResponse.LessonCardResponse resolveLessonCard(
            Lesson lesson,
            Instant now,
            List<MatchingRequestGroupItem> groupItems
    ) {
        MatchingRequestGroupItem representativeItem = findRepresentativeItem(groupItems);
        String title = resolveTitle(representativeItem, lesson.getTotalHeadcount());

        return InstructorHomeResponse.LessonCardResponse.from(
                null,
                lesson.getId(),
                resolveRemainingDays(lesson.getStatus(), lesson.getScheduledAt(), now),
                lesson.getStatus().name(),
                title,
                lesson.getScheduledAt(),
                lesson.getResort().getCode(),
                lesson.getResort().getDisplayName()
        );
    }

    // 홈 카드 대상 그룹들의 요청 목록을 한 번에 조회해 카드별 추가 조회를 막음
    private Map<Long, List<MatchingRequestGroupItem>> findGroupItemsByGroupId(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }

        return matchingRequestGroupItemRepository.findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(
                        groupIds
                )
                .stream()
                .collect(Collectors.groupingBy(item -> item.getMatchingRequestGroup().getId()));
    }

    private List<MatchingRequestGroupItem> getGroupItems(
            Map<Long, List<MatchingRequestGroupItem>> groupItemsByGroupId,
            MatchingRequestGroup matchingRequestGroup
    ) {
        return groupItemsByGroupId.getOrDefault(matchingRequestGroup.getId(), List.of());
    }

    // 그룹에 먼저 들어간 요청을 대표 요청으로 사용함
    private MatchingRequestGroupItem findRepresentativeItem(List<MatchingRequestGroupItem> groupItems) {
        if (groupItems.isEmpty()) {
            throw new BusinessException(HomeErrorCode.INSTRUCTOR_HOME_GROUP_ITEM_NOT_FOUND);
        }

        return groupItems.get(0);
    }

    // 그룹 전체 인원을 홈 카드 제목에 표시하기 위해 각 요청의 headcount 합산함
    private long sumHeadcount(List<MatchingRequestGroupItem> groupItems) {
        return groupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .mapToLong(MatchingRequest::getHeadcount)
                .sum();
    }

    // 강습 상태와 예정일 기준으로 홈 카드의 D-day 값 계산함
    private int resolveRemainingDays(LessonStatus lessonStatus, Instant scheduledAt, Instant now) {
        if (lessonStatus == LessonStatus.IN_PROGRESS) {
            return 0;
        }

        LocalDate today = now.atZone(AppZoneId.SEOUL).toLocalDate();
        LocalDate scheduledDate = scheduledAt.atZone(AppZoneId.SEOUL).toLocalDate();
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, scheduledDate));
    }

    // 강사 홈 매칭/강습 카드 제목을 대표 소비자 닉네임과 전체 인원으로 생성함
    private String resolveTitle(
            MatchingRequestGroupItem representativeItem,
            long totalHeadcount
    ) {
        return representativeItem.getMatchingRequest().getMember().getNickname() + "님 팀 " + totalHeadcount + "명";
    }
}
