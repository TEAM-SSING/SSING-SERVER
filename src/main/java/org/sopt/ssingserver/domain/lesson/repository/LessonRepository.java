package org.sopt.ssingserver.domain.lesson.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    boolean existsByInstructorProfileIdAndStatus(
            Long instructorProfileId,
            LessonStatus status
    );

    Optional<Lesson> findByMatchingOfferId(Long matchingOfferId);
}
