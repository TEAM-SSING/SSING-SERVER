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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationActor;
import org.sopt.ssingserver.domain.lesson.enums.LessonStartConfirmationStatus;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "lesson_start_confirmations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lesson_start_confirmations_lesson_member",
                        columnNames = {"lesson_id", "member_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonStartConfirmation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Member member;

    @OneToOne(fetch = FetchType.LAZY)
    private MatchingRequest matchingRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LessonStartConfirmationActor actorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LessonStartConfirmationStatus status;

    private Instant confirmedAt;
}
