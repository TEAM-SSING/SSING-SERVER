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
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

// 강사가 즉시 매칭에 노출될 때 사용할 조건 저장 엔티티
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

    private static final int MIN_HEADCOUNT = 1;
    private static final int MAX_HEADCOUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private InstructorProfile instructorProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sport sport;

    // 최종 API의 레벨 선택 목록 저장용 별도 테이블
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

    // 최종 API의 강사 가능 수업 시간 목록 저장용 별도 테이블
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
    private int maxHeadcount;

    @Column(nullable = false)
    private boolean isEquipmentReady;

    @Column(nullable = false)
    private boolean isExposed;

    // 새 강사 노출 조건 생성 시 프로필/조건값 결합 및 즉시 노출 상태 설정
    public static InstructorMatchingSetting create(
            InstructorProfile instructorProfile,
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        InstructorMatchingSetting setting = new InstructorMatchingSetting();
        setting.instructorProfile = Objects.requireNonNull(instructorProfile, "instructorProfile");
        setting.updateConditions(sport, lessonLevels, availableDurationMinutes, maxHeadcount, isEquipmentReady);
        return setting;
    }

    // 승인·설정 저장과 매칭 노출 시작을 분리하기 위한 개발 도구용 OFF 상태 생성 경로
    public static InstructorMatchingSetting createDraft(
            InstructorProfile instructorProfile,
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        InstructorMatchingSetting setting = new InstructorMatchingSetting();
        setting.instructorProfile = Objects.requireNonNull(instructorProfile, "instructorProfile");
        setting.replaceConditions(
                sport,
                lessonLevels,
                availableDurationMinutes,
                maxHeadcount,
                isEquipmentReady
        );
        setting.stopExposure();
        return setting;
    }

    // 외부의 내부 Set 직접 수정 방지를 위한 읽기 전용 view 반환
    public Set<LessonLevel> getLessonLevels() {
        return Collections.unmodifiableSet(lessonLevels);
    }

    // 후보 조회와 API 응답에서 사용할 강사 가능 시간 읽기 전용 view 반환
    public Set<Integer> getAvailableDurationMinutes() {
        return Collections.unmodifiableSet(availableDurationMinutes);
    }

    // 소비자 요청 레벨의 강사 선택 레벨 목록 포함 여부 판단
    public boolean supportsLessonLevel(LessonLevel lessonLevel) {
        return lessonLevels.contains(lessonLevel);
    }

    // 소비자 요청 시간의 강사 가능 시간 목록 포함 여부 판단
    public boolean supportsDurationMinutes(int durationMinutes) {
        return availableDurationMinutes.contains(durationMinutes);
    }

    // 강사 조건 저장 API의 갱신 지점, 레벨/시간 목록 검증 후 교체 및 즉시 노출 시작
    public void updateConditions(
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        replaceConditions(sport, lessonLevels, availableDurationMinutes, maxHeadcount, isEquipmentReady);
        // 조건 저장과 동시에 즉시 노출 시작
        startExposure();
    }

    // 노출 중인 설정을 실수로 바꾸지 않으며, 저장 뒤에도 OFF 상태를 유지한다.
    public void updateDraftConditions(
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        if (isExposed) {
            throw new IllegalStateException("Exposed matching settings cannot be reconfigured.");
        }
        replaceConditions(sport, lessonLevels, availableDurationMinutes, maxHeadcount, isEquipmentReady);
        stopExposure();
    }

    // 조건 저장 또는 ON 요청 이후 즉시 매칭 후보 조회 포함을 위한 노출 상태 ON
    public void startExposure() {
        this.isExposed = true;
    }

    // 강사의 즉시 매칭 일시 중지 시 후보 조회 제외를 위한 노출 상태 OFF
    public void stopExposure() {
        this.isExposed = false;
    }

    // 즉시 노출 시작 API 계약의 장비 준비 완료 필수 조건 검증
    private void validateEquipmentReady(boolean isEquipmentReady) {
        if (!isEquipmentReady) {
            throw new IllegalArgumentException("isEquipmentReady must be true to start exposure.");
        }
    }

    // API 계약과 후보 조회 조건 보호를 위한 최대 강습 가능 인원 범위 검증
    private void validateMaxHeadcount(int maxHeadcount) {
        if (maxHeadcount < MIN_HEADCOUNT || maxHeadcount > MAX_HEADCOUNT) {
            throw new IllegalArgumentException("maxHeadcount must be between 1 and 5.");
        }
    }

    private void replaceConditions(
            Sport sport,
            Collection<LessonLevel> lessonLevels,
            Collection<Integer> availableDurationMinutes,
            int maxHeadcount,
            boolean isEquipmentReady
    ) {
        validateEquipmentReady(isEquipmentReady);
        validateMaxHeadcount(maxHeadcount);
        Sport nextSport = Objects.requireNonNull(sport, "sport");
        LinkedHashSet<LessonLevel> nextLessonLevels = normalizedLessonLevels(lessonLevels);
        LinkedHashSet<Integer> nextAvailableDurationMinutes = normalizedAvailableDurationMinutes(
                availableDurationMinutes
        );

        this.sport = nextSport;
        this.lessonLevels.clear();
        this.lessonLevels.addAll(nextLessonLevels);
        this.availableDurationMinutes.clear();
        this.availableDurationMinutes.addAll(nextAvailableDurationMinutes);
        this.maxHeadcount = maxHeadcount;
        this.isEquipmentReady = isEquipmentReady;
    }

    // 후보 매칭 기준 보호를 위한 레벨 목록 비어 있음/null 포함 검증
    private LinkedHashSet<LessonLevel> normalizedLessonLevels(Collection<LessonLevel> lessonLevels) {
        if (lessonLevels == null || lessonLevels.isEmpty()) {
            throw new IllegalArgumentException("lessonLevels must not be empty.");
        }

        // Set 기반 중복 제거 및 리뷰/디버깅용 입력 순서 유지
        LinkedHashSet<LessonLevel> nextLessonLevels = new LinkedHashSet<>();
        for (LessonLevel lessonLevel : lessonLevels) {
            if (lessonLevel == null) {
                throw new IllegalArgumentException("lessonLevels must not contain null.");
            }
            nextLessonLevels.add(lessonLevel);
        }

        return nextLessonLevels;
    }

    // 소비자 requestedDurationMinutes 교집합 비교용 가능 시간 목록 양수 검증
    private LinkedHashSet<Integer> normalizedAvailableDurationMinutes(
            Collection<Integer> availableDurationMinutes
    ) {
        if (availableDurationMinutes == null || availableDurationMinutes.isEmpty()) {
            throw new IllegalArgumentException("availableDurationMinutes must not be empty.");
        }

        // 같은 시간 중복 전송 시 하나의 선택값 저장을 위한 Set 정규화
        LinkedHashSet<Integer> nextAvailableDurationMinutes = new LinkedHashSet<>();
        for (Integer durationMinutes : availableDurationMinutes) {
            if (durationMinutes == null) {
                throw new IllegalArgumentException("availableDurationMinutes must not contain null.");
            }
            if (durationMinutes <= 0) {
                throw new IllegalArgumentException("availableDurationMinutes must contain positive minutes.");
            }
            nextAvailableDurationMinutes.add(durationMinutes);
        }

        return nextAvailableDurationMinutes;
    }
}
