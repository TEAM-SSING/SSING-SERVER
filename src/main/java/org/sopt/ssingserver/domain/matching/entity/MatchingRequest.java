package org.sopt.ssingserver.domain.matching.entity;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "matching_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Resort resort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sport sport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LessonLevel lessonLevel;

    @Column(nullable = false)
    private int headcount;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private boolean isEquipmentReady;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MatchingRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MatchingRequestStatusReason statusReason;

    @ManyToOne(fetch = FetchType.LAZY)
    private MatchingOffer matchingOffer;

    // 요청, 최종 확인, 결제처럼 현재 진행 단계에서 앱이 참고할 만료 시각
    private Instant expiresAt;

    public static MatchingRequest create(
            Member member,
            Resort resort,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            int durationMinutes,
            boolean isEquipmentReady,
            Instant expiresAt
    ) {
        MatchingRequest matchingRequest = new MatchingRequest();
        matchingRequest.member = member;
        matchingRequest.resort = resort;
        matchingRequest.sport = sport;
        matchingRequest.lessonLevel = lessonLevel;
        matchingRequest.headcount = headcount;
        matchingRequest.durationMinutes = durationMinutes;
        matchingRequest.isEquipmentReady = isEquipmentReady;
        matchingRequest.status = MatchingRequestStatus.REQUESTED;
        matchingRequest.expiresAt = expiresAt;
        return matchingRequest;
    }

    public void markGrouped() {
        updateStatus(MatchingRequestStatus.GROUPED, null);
    }

    public void markMatched(MatchingOffer matchingOffer, Instant expiresAt) {
        this.matchingOffer = matchingOffer;
        this.expiresAt = expiresAt;
        updateStatus(MatchingRequestStatus.MATCHED, null);
    }

    public void confirm() {
        updateStatus(MatchingRequestStatus.CONFIRMED, null);
    }

    public void complete() {
        updateStatus(MatchingRequestStatus.COMPLETED, null);
    }

    public void cancelByConsumer() {
        updateStatus(MatchingRequestStatus.CANCELED, MatchingRequestStatusReason.CONSUMER_CANCELED);
    }

    public void failNoAvailableInstructor() {
        updateStatus(MatchingRequestStatus.FAILED, MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR);
    }

    public void expire(MatchingRequestStatusReason statusReason) {
        updateStatus(MatchingRequestStatus.EXPIRED, statusReason);
    }

    private void updateStatus(
            MatchingRequestStatus status,
            MatchingRequestStatusReason statusReason
    ) {
        this.status = status;
        this.statusReason = statusReason;
    }
}
