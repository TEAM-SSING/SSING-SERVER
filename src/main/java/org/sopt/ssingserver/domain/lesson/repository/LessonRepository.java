package org.sopt.ssingserver.domain.lesson.repository;

import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    boolean existsByInstructorProfileIdAndStatus(
            Long instructorProfileId,
            LessonStatus status
    );

    Optional<Lesson> findByMatchingOfferId(Long matchingOfferId);

    // 강사 홈에 표시할 예정/진행 강습을 가까운 일정 순으로 조회함
    @EntityGraph(attributePaths = {
            "resort",
            "matchingOffer",
            "matchingOffer.matchingRequestGroup"
    })
    List<Lesson> findByInstructorProfileIdAndStatusInOrderByScheduledAtAscIdAsc(
            Long instructorProfileId,
            List<LessonStatus> statuses
    );
}
