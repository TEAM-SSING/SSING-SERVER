package org.sopt.ssingserver.domain.lesson.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    boolean existsByInstructorProfileIdAndStatus(
            Long instructorProfileId,
            LessonStatus status
    );

    long countByInstructorProfileIdAndStatus(
            Long instructorProfileId,
            LessonStatus status
    );

    Optional<Lesson> findByMatchingOfferId(Long matchingOfferId);

    @Query("""
            select lesson
            from Lesson lesson
            join fetch lesson.matchingOffer matchingOffer
            join fetch lesson.instructorProfile
            where matchingOffer.id in :matchingOfferIds
            order by matchingOffer.id asc, lesson.id desc
            """)
    List<Lesson> findByMatchingOfferIdInOrderByOfferIdAscLessonIdDesc(
            @Param("matchingOfferIds") Collection<Long> matchingOfferIds
    );

    @EntityGraph(attributePaths = {
            "resort",
            "instructorProfile",
            "instructorProfile.member",
            "matchingOffer"
    })
    @Query("select lesson from Lesson lesson where lesson.id = :id")
    Optional<Lesson> findWithDetailById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "instructorProfile",
            "instructorProfile.member"
    })
    @Query("select lesson from Lesson lesson where lesson.id = :id")
    Optional<Lesson> findByIdForUpdate(@Param("id") Long id);

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
