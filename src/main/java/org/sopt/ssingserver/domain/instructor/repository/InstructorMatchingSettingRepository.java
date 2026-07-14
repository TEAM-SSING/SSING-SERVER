package org.sopt.ssingserver.domain.instructor.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.repository.projection.InstructorMatchingCandidateIdProjection;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

// 즉시 매칭 탐색의 강사 노출 조건 조회 Repository
public interface InstructorMatchingSettingRepository extends JpaRepository<InstructorMatchingSetting, Long> {

    // 강사 본인 노출 조건 조회/수정 API의 기존 설정 조회
    Optional<InstructorMatchingSetting> findByInstructorProfileId(Long instructorProfileId);

    boolean existsByInstructorProfileId(Long instructorProfileId);

    // 조건 수정/노출 중단 writer가 후보 선정과 같은 setting root row를 먼저 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select setting
            from InstructorMatchingSetting setting
            where setting.instructorProfile.id = :instructorProfileId
            """)
    Optional<InstructorMatchingSetting> findByInstructorProfileIdForUpdate(
            @Param("instructorProfileId") Long instructorProfileId
    );

    long countByIsExposedTrue();

    // 잠금 전 엔티티 적재를 피하기 위한 노출 후보 식별자 projection 조회
    @Query("""
            select distinct
                   setting.id as settingId,
                   setting.instructorProfile.id as instructorProfileId
            from InstructorMatchingSetting setting
            join setting.lessonLevels lessonLevel
            join setting.availableDurationMinutes availableDurationMinutes
            where setting.instructorProfile.resort = :resort
              and setting.sport = :sport
              and lessonLevel = :lessonLevel
              and availableDurationMinutes in :requestedDurationMinutes
              and setting.maxHeadcount >= :headcount
              and setting.isEquipmentReady = :isEquipmentReady
              and setting.isExposed = true
            order by setting.instructorProfile.id asc, setting.id asc
            """)
    List<InstructorMatchingCandidateIdProjection> findExposedCandidateIds(
            @Param("resort") Resort resort,
            @Param("sport") Sport sport,
            @Param("lessonLevel") LessonLevel lessonLevel,
            @Param("headcount") int headcount,
            @Param("requestedDurationMinutes") Collection<Integer> requestedDurationMinutes,
            @Param("isEquipmentReady") boolean isEquipmentReady
    );

    // 후보 선택 직전 setting 단일 row 잠금, 동시 트리거와 조건 writer를 같은 기준으로 직렬화
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select setting from InstructorMatchingSetting setting where setting.id = :id")
    Optional<InstructorMatchingSetting> findByIdForUpdate(@Param("id") Long id);
}
