package org.sopt.ssingserver.domain.lesson.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "lessons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private InstructorProfile instructorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Resort resort;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingOffer matchingOffer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sport sport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LessonLevel lessonLevel;

    @Column(nullable = false)
    private int totalHeadcount;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(length = 200)
    private String meetingPlace;

    @Column(nullable = false)
    private Instant confirmedAt;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LessonStatus status;

    private Instant startedAt;

    private Instant completedAt;

    private Instant canceledAt;

    public static Lesson createImmediateConfirmed(
            InstructorProfile instructorProfile,
            Resort resort,
            MatchingOffer matchingOffer,
            Sport sport,
            LessonLevel lessonLevel,
            int totalHeadcount,
            int durationMinutes,
            Instant confirmedAt
    ) {
        Lesson lesson = new Lesson();
        lesson.instructorProfile = instructorProfile;
        lesson.resort = resort;
        lesson.matchingOffer = matchingOffer;
        lesson.sport = sport;
        lesson.lessonLevel = lessonLevel;
        lesson.totalHeadcount = totalHeadcount;
        lesson.durationMinutes = durationMinutes;
        // 즉시 매칭은 예약 시간이 없으므로 확정 시각을 홈 카드 기준 시각으로 함께 저장한다.
        lesson.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt must not be null.");
        lesson.scheduledAt = confirmedAt;
        lesson.status = LessonStatus.CONFIRMED;
        return lesson;
    }

    public void start(Instant startedAt) {
        this.status = LessonStatus.IN_PROGRESS;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null.");
    }

    public void complete(Instant completedAt) {
        this.status = LessonStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null.");
    }

    public void cancel(Instant canceledAt) {
        this.status = LessonStatus.CANCELED;
        this.canceledAt = Objects.requireNonNull(canceledAt, "canceledAt must not be null.");
    }
}
