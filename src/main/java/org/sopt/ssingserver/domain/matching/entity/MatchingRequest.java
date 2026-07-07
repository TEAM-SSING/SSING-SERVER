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

// 소비자 즉시 매칭 요청 저장용 핵심 요청 엔티티
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
    private int requestedDurationMinutes;

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

    // 매칭 요청 생성 시 DB REQUESTED 시작 및 SEARCHING의 API 표시 상태 계산
    public static MatchingRequest create(
            Member member,
            Resort resort,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            int requestedDurationMinutes,
            boolean isEquipmentReady,
            Instant expiresAt
    ) {
        MatchingRequest matchingRequest = new MatchingRequest();
        matchingRequest.member = member;
        matchingRequest.resort = resort;
        matchingRequest.sport = sport;
        matchingRequest.lessonLevel = lessonLevel;
        matchingRequest.headcount = headcount;
        matchingRequest.requestedDurationMinutes = requestedDurationMinutes;
        matchingRequest.isEquipmentReady = isEquipmentReady;
        matchingRequest.status = MatchingRequestStatus.REQUESTED;
        matchingRequest.expiresAt = expiresAt;
        return matchingRequest;
    }

    // 현재 시각 기준 탐색 만료 시각 도달 여부와 5분 SEARCHING 종료 여부 판단
    public boolean isSearchExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    // 요청의 그룹 편입 이후 순수 탐색 대상 제외를 위한 GROUPED 전환
    public void markGrouped() {
        updateStatus(MatchingRequestStatus.GROUPED, null);
    }

    // 강사 제안 수락 이후 수락 제안과 소비자 최종 확인 만료 시각 저장
    public void markMatched(MatchingOffer matchingOffer, Instant expiresAt) {
        this.matchingOffer = matchingOffer;
        this.expiresAt = expiresAt;
        updateStatus(MatchingRequestStatus.MATCHED, null);
    }

    // 소비자 강사 매칭 최종 수락 이후 결제 전 CONFIRMED 요청 상태 이동
    public void confirm() {
        updateStatus(MatchingRequestStatus.CONFIRMED, null);
    }

    // 결제 완료와 수업 확정 이후 COMPLETED 상태 종료
    public void complete() {
        updateStatus(MatchingRequestStatus.COMPLETED, null);
    }

    // 소비자 직접 중지 요청의 취소 사유 저장 및 상태 조회 원인 구분
    public void cancelByConsumer() {
        updateStatus(MatchingRequestStatus.CANCELED, MatchingRequestStatusReason.CONSUMER_CANCELED);
    }

    // SEARCHING 만료까지 후보 없음인 경우의 최종 실패 상태 저장
    public void failNoAvailableInstructor() {
        updateStatus(MatchingRequestStatus.FAILED, MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR);
    }

    // 강사 응답 제한 시간 초과 요청의 강사 타임아웃 사유 만료 처리
    public void expireByInstructorTimeout() {
        expireWithReason(MatchingRequestStatusReason.INSTRUCTOR_TIMEOUT);
    }

    // 소비자 최종 확인 제한 시간 초과 요청의 확인 타임아웃 사유 만료 처리
    public void expireByConfirmationTimeout() {
        expireWithReason(MatchingRequestStatusReason.CONFIRMATION_TIMEOUT);
    }

    // 결제 제한 시간 초과 요청의 결제 타임아웃 사유 만료 처리
    public void expireByPaymentTimeout() {
        expireWithReason(MatchingRequestStatusReason.PAYMENT_TIMEOUT);
    }

    // 여러 만료 경로의 공통 EXPIRED 상태 전환 규칙 집계
    private void expireWithReason(MatchingRequestStatusReason statusReason) {
        updateStatus(MatchingRequestStatus.EXPIRED, statusReason);
    }

    // 요청 상태와 사유 동시 변경을 통한 이전 사유 잔존 방지
    private void updateStatus(
            MatchingRequestStatus status,
            MatchingRequestStatusReason statusReason
    ) {
        this.status = status;
        this.statusReason = statusReason;
    }
}
