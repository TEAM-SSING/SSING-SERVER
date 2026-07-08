package org.sopt.ssingserver.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse.LessonCardResponse;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.projection.HomeLessonCardProjection;

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

    @Test
    void getHome은_예정된_강습을_D_day와_함께_반환한다() {
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

        ConsumerHomeResponse response = service.getHome(1L);

        assertThat(response.hasUnreadNotification()).isFalse();
        assertThat(response.lessonCards()).hasSize(1);
        LessonCardResponse lesson = response.lessonCards().get(0);
        assertThat(lesson.lessonId()).isEqualTo(1L);
        assertThat(lesson.lessonStatus()).isSameAs(LessonStatus.CONFIRMED);
        assertThat(lesson.remainingDays()).isEqualTo(2);
        assertThat(lesson.title()).isEqualTo("김철수님 팀 4명");
        assertThat(lesson.scheduledAt()).isEqualTo(OffsetDateTime.of(2026, 7, 11, 19, 0, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(lesson.resort().code()).isEqualTo("HIGH1");
        assertThat(lesson.resort().displayName()).isEqualTo("하이원");
    }

    @Test
    void getHome은_진행중인_강습의_remainingDays를_0으로_고정한다() {
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

        ConsumerHomeResponse response = service.getHome(1L);

        LessonCardResponse lesson = response.lessonCards().get(0);
        assertThat(lesson.lessonStatus()).isSameAs(LessonStatus.IN_PROGRESS);
        assertThat(lesson.remainingDays()).isZero();
        assertThat(lesson.title()).isEqualTo("박영희님 팀 2명");
    }

    @Test
    void getHome은_조회된_강습_카드를_순서대로_반환한다() {
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

        ConsumerHomeResponse response = service.getHome(1L);

        assertThat(response.lessonCards())
                .extracting(LessonCardResponse::lessonId)
                .containsExactly(1L, 2L);
    }

    @Test
    void getHome은_예약된_강습이_없으면_빈_배열을_반환한다() {
        HomeService service = createService();
        when(lessonParticipantRepository.findHomeLessonCardsByMemberIdAndLessonStatusIn(1L, UPCOMING_LESSON_STATUSES))
                .thenReturn(List.of());

        ConsumerHomeResponse response = service.getHome(1L);

        assertThat(response.lessonCards()).isEmpty();
        assertThat(response.hasUnreadNotification()).isFalse();
    }

    private HomeService createService() {
        return new HomeService(lessonParticipantRepository, FIXED_CLOCK);
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
