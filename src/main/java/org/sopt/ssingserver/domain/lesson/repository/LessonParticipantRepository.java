package org.sopt.ssingserver.domain.lesson.repository;

import java.util.Collection;
import java.util.List;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.projection.HomeLessonCardProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonParticipantRepository extends JpaRepository<LessonParticipant, Long> {

    // 대표 회원의 홈 카드에 필요한 값만 조회
    @Query("""
            select distinct
                   lesson.id as lessonId,
                   lesson.status as lessonStatus,
                   lesson.scheduledAt as scheduledAt,
                   member.nickname as requesterNickname,
                   lesson.totalHeadcount as totalHeadcount,
                   resort.code as resortCode,
                   resort.displayName as resortDisplayName
            from LessonParticipant lessonParticipant
            join lessonParticipant.lesson lesson
            join lesson.resort resort
            join lessonParticipant.matchingRequest matchingRequest
            join matchingRequest.member member
            where member.id = :memberId
              and lesson.status in :statuses
            order by lesson.scheduledAt asc
            """)
    List<HomeLessonCardProjection> findHomeLessonCardsByMemberIdAndLessonStatusIn(
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<LessonStatus> statuses
    );

}
