package org.sopt.ssingserver.domain.instructor.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
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

    // 소비자 요청 조건과 강사 노출 조건이 모두 맞고 희망 시간 교집합이 있는 강사 후보 조회
    @Query("""
            select distinct setting
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
    List<InstructorMatchingSetting> findExposedCandidates(
            @Param("resort") Resort resort,
            @Param("sport") Sport sport,
            @Param("lessonLevel") LessonLevel lessonLevel,
            @Param("headcount") int headcount,
            @Param("requestedDurationMinutes") Collection<Integer> requestedDurationMinutes,
            @Param("isEquipmentReady") boolean isEquipmentReady
    );

    // 후보 선택 직전 강사 노출 조건 row 잠금 재조회, 동시 트리거의 같은 강사 중복 제안 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select distinct setting
            from InstructorMatchingSetting setting
            join setting.lessonLevels lessonLevel
            join setting.availableDurationMinutes availableDurationMinutes
            where setting.id = :id
              and setting.instructorProfile.resort = :resort
              and setting.sport = :sport
              and lessonLevel = :lessonLevel
              and availableDurationMinutes in :requestedDurationMinutes
              and setting.maxHeadcount >= :headcount
              and setting.isEquipmentReady = :isEquipmentReady
              and setting.isExposed = true
            """)
    Optional<InstructorMatchingSetting> findExposedCandidateByIdForUpdate(
            @Param("id") Long id,
            @Param("resort") Resort resort,
            @Param("sport") Sport sport,
            @Param("lessonLevel") LessonLevel lessonLevel,
            @Param("headcount") int headcount,
            @Param("requestedDurationMinutes") Collection<Integer> requestedDurationMinutes,
            @Param("isEquipmentReady") boolean isEquipmentReady
    );
}
