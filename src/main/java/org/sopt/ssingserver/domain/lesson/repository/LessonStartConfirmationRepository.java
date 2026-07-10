package org.sopt.ssingserver.domain.lesson.repository;

import java.util.List;
import org.sopt.ssingserver.domain.lesson.entity.LessonStartConfirmation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonStartConfirmationRepository extends JpaRepository<LessonStartConfirmation, Long> {

    @EntityGraph(attributePaths = {"matchingRequest"})
    List<LessonStartConfirmation> findByLessonId(Long lessonId);
}
