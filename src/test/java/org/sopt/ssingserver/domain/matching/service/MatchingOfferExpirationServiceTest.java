package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.dto.result.NextMatchingOfferResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchingOfferExpirationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T00:01:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingRequestGroupRepository matchingRequestGroupRepository;

    @Mock
    private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

    @Mock
    private MatchingSearchService matchingSearchService;

    @Test
    void expireOffer는_만료된_OFFERED_제안을_EXPIRED로_바꾸고_다음_후보가_있으면_그룹을_EXPOSED로_유지한다() {
        MatchingOfferExpirationService service = createService();
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingRequest matchingRequest = matchingRequest(30L);
        matchingRequest.markGrouped();
        MatchingRequestGroupItem item = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, group, Instant.parse("2026-07-07T00:00:59Z"));
        MatchingOffer nextOffer = offeredOffer(51L, group, Instant.parse("2026-07-07T00:02:00Z"));
        givenOfferWithGroupItems(offer, group, List.of(item));
        when(matchingSearchService.ensureNextOfferForGroup(matchingRequest, group, FIXED_CLOCK.instant()))
                .thenReturn(NextMatchingOfferResult.created(nextOffer));

        service.expireOffer(50L);

        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.EXPIRED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPOSED);
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.GROUPED);
    }

    @Test
    void expireOffer는_다음_후보가_없으면_그룹을_EXPIRED로_닫고_요청을_REMATCHING용_REQUESTED로_되돌린다() {
        MatchingOfferExpirationService service = createService();
        MatchingRequestGroup group = exposedGroup(20L);
        MatchingRequest matchingRequest = matchingRequest(30L);
        matchingRequest.markGrouped();
        MatchingRequestGroupItem item = item(40L, matchingRequest, group);
        MatchingOffer offer = offeredOffer(50L, group, Instant.parse("2026-07-07T00:00:59Z"));
        givenOfferWithGroupItems(offer, group, List.of(item));
        when(matchingSearchService.ensureNextOfferForGroup(matchingRequest, group, FIXED_CLOCK.instant()))
                .thenReturn(NextMatchingOfferResult.noCandidate());

        service.expireOffer(50L);

        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.EXPIRED);
        assertThat(group.getStatus()).isSameAs(MatchingRequestGroupStatus.EXPIRED);
        assertThat(matchingRequest.getStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(matchingRequest.getStatusReason()).isSameAs(MatchingRequestStatusReason.INSTRUCTOR_TIMEOUT);
    }

    @Test
    void expireOffer는_아직_만료되지_않은_제안이면_아무_상태도_바꾸지_않는다() {
        MatchingOfferExpirationService service = createService();
        MatchingOffer offer = offeredOffer(
                50L,
                exposedGroup(20L),
                Instant.parse("2026-07-07T00:01:01Z")
        );
        when(matchingOfferRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(offer));

        service.expireOffer(50L);

        assertThat(offer.getStatus()).isSameAs(MatchingOfferStatus.OFFERED);
        verifyNoInteractions(matchingRequestGroupRepository);
        verifyNoInteractions(matchingRequestGroupItemRepository);
        verifyNoInteractions(matchingSearchService);
    }

    private MatchingOfferExpirationService createService() {
        return new MatchingOfferExpirationService(
                matchingOfferRepository,
                matchingRequestGroupRepository,
                matchingRequestGroupItemRepository,
                matchingSearchService,
                FIXED_CLOCK
        );
    }

    private void givenOfferWithGroupItems(
            MatchingOffer offer,
            MatchingRequestGroup group,
            List<MatchingRequestGroupItem> groupItems
    ) {
        when(matchingOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(matchingRequestGroupRepository.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(matchingRequestGroupItemRepository.findByMatchingRequestGroupIdForUpdate(group.getId()))
                .thenReturn(groupItems);
    }

    private MatchingOffer offeredOffer(
            Long id,
            MatchingRequestGroup group,
            Instant expiresAt
    ) {
        MatchingOffer offer = MatchingOffer.create(
                instructorProfile(10L),
                group,
                Instant.parse("2026-07-07T00:00:00Z"),
                expiresAt
        );
        ReflectionTestUtils.setField(offer, "id", id);
        return offer;
    }

    private MatchingRequest matchingRequest(Long id) {
        MatchingRequest matchingRequest = MatchingRequest.createUnlimitedSearch(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
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

    private MatchingRequestGroup exposedGroup(Long id) {
        MatchingRequestGroup group = MatchingRequestGroup.createCandidate(120);
        group.expose();
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private MatchingRequestGroupItem item(
            Long id,
            MatchingRequest matchingRequest,
            MatchingRequestGroup group
    ) {
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(matchingRequest, group);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private InstructorProfile instructorProfile(Long id) {
        InstructorProfile instructorProfile = InstructorProfile.create(
                Member.create("강사", null, MemberRole.INSTRUCTOR, MemberStatus.ACTIVE),
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

    private Resort resort() {
        Resort resort = construct(Resort.class);
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
}
