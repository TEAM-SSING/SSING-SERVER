package org.sopt.ssingserver.domain.matching.dto.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingCreationResultTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-07-07T00:05:00Z");

    @Test
    void searching은_무제한탐색_요청의_최초응답에서_만료시각을_비운다() {
        MatchingRequest matchingRequest = unlimitedSearchRequest();
        ReflectionTestUtils.setField(matchingRequest, "id", 10L);

        MatchingCreationResult result = MatchingCreationResult.searching(matchingRequest);

        assertThat(result.matchingRequestId()).isEqualTo(10L);
        assertThat(result.matchingStatus()).isSameAs(MatchingStatus.SEARCHING);
        assertThat(result.requestStatus()).isSameAs(MatchingRequestStatus.REQUESTED);
        assertThat(result.requestStatusReason()).isNull();
        assertThat(result.groupId()).isNull();
        assertThat(result.groupStatus()).isNull();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    void searching은_fallback_만료시각이_있는_요청이면_만료시각을_전달한다() {
        MatchingRequest matchingRequest = fallbackSearchRequest();

        MatchingCreationResult result = MatchingCreationResult.searching(matchingRequest);

        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    private MatchingRequest unlimitedSearchRequest() {
        return MatchingRequest.createUnlimitedSearch(
                member(),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                List.of(120, 180),
                true
        );
    }

    private MatchingRequest fallbackSearchRequest() {
        return MatchingRequest.create(
                member(),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                2,
                List.of(120, 180),
                true,
                EXPIRES_AT
        );
    }

    private Member member() {
        return Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE);
    }

    private Resort resort() {
        try {
            Constructor<Resort> constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Resort resort = constructor.newInstance();
            ReflectionTestUtils.setField(resort, "code", "HIGH1");
            ReflectionTestUtils.setField(resort, "name", "하이원리조트");
            ReflectionTestUtils.setField(resort, "displayName", "하이원");
            return resort;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
