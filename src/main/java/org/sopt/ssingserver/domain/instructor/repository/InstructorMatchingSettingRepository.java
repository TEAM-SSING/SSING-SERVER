package org.sopt.ssingserver.domain.instructor.repository;

import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstructorMatchingSettingRepository extends JpaRepository<InstructorMatchingSetting, Long> {

    Optional<InstructorMatchingSetting> findByInstructorProfileId(Long instructorProfileId);

    @Query("""
            select distinct setting
            from InstructorMatchingSetting setting
            join setting.lessonLevels lessonLevel
            where setting.instructorProfile.resort = :resort
              and setting.sport = :sport
              and lessonLevel = :lessonLevel
              and setting.maxHeadcount >= :headcount
              and setting.isEquipmentReady = :isEquipmentReady
              and setting.isExposed = true
            """)
    List<InstructorMatchingSetting> findExposedCandidates(
            @Param("resort") Resort resort,
            @Param("sport") Sport sport,
            @Param("lessonLevel") LessonLevel lessonLevel,
            @Param("headcount") int headcount,
            @Param("isEquipmentReady") boolean isEquipmentReady
    );
}
