package org.sopt.ssingserver.domain.lesson.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "lesson_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lesson_participants_lesson_request_participant",
                        columnNames = {"lesson_id", "matching_request_participant_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequest matchingRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MatchingRequestParticipant matchingRequestParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @Column(nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    public static LessonParticipant create(
            Lesson lesson,
            MatchingRequest matchingRequest,
            MatchingRequestParticipant matchingRequestParticipant
    ) {
        LessonParticipant lessonParticipant = new LessonParticipant();
        lessonParticipant.lesson = lesson;
        lessonParticipant.matchingRequest = matchingRequest;
        lessonParticipant.matchingRequestParticipant = matchingRequestParticipant;
        lessonParticipant.member = null;
        lessonParticipant.age = matchingRequestParticipant.getAge();
        lessonParticipant.gender = matchingRequestParticipant.getGender();
        return lessonParticipant;
    }
}
