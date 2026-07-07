package org.sopt.ssingserver.domain.instructor.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "instructor_matching_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_instructor_matching_settings_instructor_profile",
                columnNames = "instructor_profile_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorMatchingSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private InstructorProfile instructorProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sport sport;

    // 레벨 - 여러 개 다중 선택이므로 별도 테이블로 분리
    @ElementCollection(fetch = FetchType.LAZY)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "instructor_matching_settings_lesson_levels",
            joinColumns = @JoinColumn(name = "instructor_matching_setting_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_instructor_matching_settings_lesson_level",
                    columnNames = {"instructor_matching_setting_id", "lesson_level"}
            )
    )
    @Column(name = "lesson_level", nullable = false, length = 30)
    private Set<LessonLevel> lessonLevels = new LinkedHashSet<>();

    @Column(nullable = false)
    private int maxHeadcount;

    // 강습 시간 - 여러 개 다중 선택이므로 별도 테이블로 분리
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "instructor_matching_settings_available_durations",
            joinColumns = @JoinColumn(name = "instructor_matching_setting_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_instructor_matching_settings_available_duration",
                    columnNames = {"instructor_matching_setting_id", "available_duration_minutes"}
            )
    )
    @Column(name = "available_duration_minutes", nullable = false)
    private Set<Integer> availableDurationMinutes = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean isEquipmentReady;

    @Column(nullable = false)
    private boolean isExposed;

    // 생성/상태 변경
    public static InstructorMatchingSetting create(
            InstructorProfile instructorProfile,
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        InstructorMatchingSetting setting = new InstructorMatchingSetting();
        setting.instructorProfile = instructorProfile;
        setting.updateConditions(sport, lessonLevels, availableDurationMinutes, maxHeadcount, isEquipmentReady);
        return setting;
    }

    public void updateConditions(
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        this.sport = sport;
        replaceLessonLevels(lessonLevels);
        replaceAvailableDurationMinutes(availableDurationMinutes);
        this.maxHeadcount = maxHeadcount;
        this.isEquipmentReady = isEquipmentReady;
        // 조건 저장 API는 저장과 동시에 즉시 노출을 시작한다.
        startExposure();
    }

    public void startExposure() {
        this.isExposed = true;
    }

    public void stopExposure() {
        this.isExposed = false;
    }

    // 조회
    public Set<LessonLevel> getLessonLevels() {
        return Collections.unmodifiableSet(lessonLevels);
    }

    public boolean supportsLessonLevel(LessonLevel lessonLevel) {
        return lessonLevels.contains(lessonLevel);
    }

    public Set<Integer> getAvailableDurationMinutes() {
        return Collections.unmodifiableSet(availableDurationMinutes);
    }

    public boolean supportsDurationMinutes(int durationMinutes) {
        return availableDurationMinutes.contains(durationMinutes);
    }

    private void replaceLessonLevels(Collection<LessonLevel> lessonLevels) {
        if (lessonLevels == null || lessonLevels.isEmpty()) {
            throw new IllegalArgumentException("lessonLevels must not be empty.");
        }

        LinkedHashSet<LessonLevel> nextLessonLevels = new LinkedHashSet<>();
        for (LessonLevel lessonLevel : lessonLevels) {
            if (lessonLevel == null) {
                throw new IllegalArgumentException("lessonLevels must not contain null.");
            }
            nextLessonLevels.add(lessonLevel);
        }

        this.lessonLevels.clear();
        this.lessonLevels.addAll(nextLessonLevels);
    }

    private void replaceAvailableDurationMinutes(Collection<Integer> availableDurationMinutes) {
        if (availableDurationMinutes == null || availableDurationMinutes.isEmpty()) {
            throw new IllegalArgumentException("availableDurationMinutes must not be empty.");
        }

        LinkedHashSet<Integer> nextAvailableDurationMinutes = new LinkedHashSet<>();
        for (Integer availableDurationMinute : availableDurationMinutes) {
            if (availableDurationMinute == null) {
                throw new IllegalArgumentException("availableDurationMinutes must not contain null.");
            }
            nextAvailableDurationMinutes.add(availableDurationMinute);
        }

        this.availableDurationMinutes.clear();
        this.availableDurationMinutes.addAll(nextAvailableDurationMinutes);
    }
}
