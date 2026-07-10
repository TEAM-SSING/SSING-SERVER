package org.sopt.ssingserver.domain.lesson.repository;

import java.util.List;
import org.sopt.ssingserver.domain.lesson.entity.LessonCancellation;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancellationActor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonCancellationRepository extends JpaRepository<LessonCancellation, Long> {

    @EntityGraph(attributePaths = {"member"})
    List<LessonCancellation> findByLessonId(Long lessonId);

    @EntityGraph(attributePaths = {"member"})
    List<LessonCancellation> findByLessonIdAndMatchingRequestId(Long lessonId, Long matchingRequestId);

    List<LessonCancellation> findByLessonIdAndCanceledBy(Long lessonId, LessonCancellationActor canceledBy);
}
