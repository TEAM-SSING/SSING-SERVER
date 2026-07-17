package org.sopt.ssingserver.domain.matching.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingRequestParticipantTest {

    @Test
    void create는_매칭요청_참여자정보를_저장한다() {
        MatchingRequest matchingRequest = matchingRequest();

        MatchingRequestParticipant participant = MatchingRequestParticipant.create(
                matchingRequest,
                "  홍길동  ",
                24,
                Gender.FEMALE
        );

        assertThat(participant.getMatchingRequest()).isSameAs(matchingRequest);
        assertThat(participant.getName()).isEqualTo("홍길동");
        assertThat(participant.getAge()).isEqualTo(24);
        assertThat(participant.getGender()).isSameAs(Gender.FEMALE);
    }

    @Test
    void create는_기존_요청의_null_이름을_허용한다() {
        MatchingRequestParticipant participant = MatchingRequestParticipant.create(
                matchingRequest(),
                null,
                24,
                Gender.FEMALE
        );

        assertThat(participant.getName()).isNull();
    }

    private MatchingRequest matchingRequest() {
        return MatchingRequest.create(
                Member.create("소비자", null, MemberRole.CONSUMER, MemberStatus.ACTIVE),
                resort(),
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                1,
                List.of(120),
                true
        );
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
