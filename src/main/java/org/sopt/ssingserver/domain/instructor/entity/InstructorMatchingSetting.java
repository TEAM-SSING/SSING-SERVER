package org.sopt.ssingserver.domain.instructor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "instructor_matching_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorMatchingSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private InstructorProfile instructorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Resort resort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sport sport;

    // 최종 API는 레벨 범위가 아니라 선택 목록을 받으므로 별도 테이블로 보관한다.
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

    @Column(nullable = false)
    private boolean isEquipmentReady;

    @Column(nullable = false)
    private boolean isExposed;

    public static InstructorMatchingSetting create(
            InstructorProfile instructorProfile,
            Resort resort,
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        InstructorMatchingSetting setting = new InstructorMatchingSetting();
        setting.instructorProfile = instructorProfile;
        setting.resort = resort;
        setting.updateConditions(sport, lessonLevels, maxHeadcount, isEquipmentReady);
        return setting;
    }

    public Set<LessonLevel> getLessonLevels() {
        return Collections.unmodifiableSet(lessonLevels);
    }

    public boolean supportsLessonLevel(LessonLevel lessonLevel) {
        return lessonLevels.contains(lessonLevel);
    }

    public void updateConditions(
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        this.sport = sport;
        replaceLessonLevels(lessonLevels);
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
}
