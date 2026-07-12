package org.sopt.ssingserver.domain.lesson.repository;

import java.util.Collection;
import java.util.List;
import org.sopt.ssingserver.domain.lesson.entity.LessonParticipant;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.projection.HomeLessonCardProjection;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
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
                   lesson.sport as sport,
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

    // 강습 상세에 표시할 전체 강습생을 팀별 순서로 조회
    @Query("""
            select lessonParticipant
            from LessonParticipant lessonParticipant
            join fetch lessonParticipant.matchingRequest matchingRequest
            join fetch matchingRequest.member
            where lessonParticipant.lesson.id = :lessonId
            order by matchingRequest.id asc, lessonParticipant.id asc
            """)
    List<LessonParticipant> findDetailParticipantsByLessonId(@Param("lessonId") Long lessonId);

    // 강습 참여 팀(소비자)을 중복 없이 조회
    @Query("""
            select distinct matchingRequest
            from LessonParticipant lessonParticipant
            join lessonParticipant.matchingRequest matchingRequest
            join fetch matchingRequest.member
            where lessonParticipant.lesson.id = :lessonId
            order by matchingRequest.id asc
            """)
    List<MatchingRequest> findDistinctMatchingRequestsByLessonId(@Param("lessonId") Long lessonId);

    // 로그인한 회원이 해당 강습에 참여한 팀의 매칭 요청 ID를 조회
    @Query("""
            select distinct matchingRequest.id
            from LessonParticipant lessonParticipant
            join lessonParticipant.matchingRequest matchingRequest
            join matchingRequest.member member
            where lessonParticipant.lesson.id = :lessonId
              and member.id = :memberId
            """)
    List<Long> findMatchingRequestIdsByLessonIdAndMemberId(
            @Param("lessonId") Long lessonId,
            @Param("memberId") Long memberId
    );

}
