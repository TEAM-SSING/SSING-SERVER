package org.sopt.ssingserver.domain.matching.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingRequestGroupItemTest {

    @Test
    void createNotRequested는_아직_요청하지_않은_그룹항목으로_초기화한다() {
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(
                matchingRequest(),
                MatchingRequestGroup.createCandidate()
        );

        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.NOT_REQUESTED);
        assertThat(item.getRespondedAt()).isNull();
    }

    @Test
    void 응답_메서드는_상태와_응답시각을_저장한다() {
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(
                matchingRequest(),
                MatchingRequestGroup.createCandidate()
        );
        Instant respondedAt = Instant.parse("2026-07-07T00:01:00Z");

        item.requestConfirmation();
        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.PENDING);

        item.accept(respondedAt);
        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.ACCEPTED);
        assertThat(item.getRespondedAt()).isEqualTo(respondedAt);

        item.reject(respondedAt.plusSeconds(1));
        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.REJECTED);
        assertThat(item.getRespondedAt()).isEqualTo(respondedAt.plusSeconds(1));
    }

    @Test
    void 닫기_메서드는_의도에_맞는_항목상태를_저장한다() {
        MatchingRequestGroupItem item = MatchingRequestGroupItem.createNotRequested(
                matchingRequest(),
                MatchingRequestGroup.createCandidate()
        );

        item.cancel();
        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.CANCELED);

        item.expire();
        assertThat(item.getStatus()).isSameAs(MatchingRequestGroupItemStatus.EXPIRED);
    }

    private MatchingRequest matchingRequest() {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                1,
                120,
                true,
                Instant.parse("2026-07-07T00:10:00Z")
        );
    }

    private Resort resort() {
        try {
            Constructor<Resort> constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Resort resort = constructor.newInstance();
            ReflectionTestUtils.setField(resort, "code", "HIGH1");
            ReflectionTestUtils.setField(resort, "name", "하이원");
            return resort;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
