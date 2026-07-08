package org.sopt.ssingserver.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
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

    // 최종 API의 소비자 희망 수업 시간 목록 저장용 별도 테이블
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "matching_requests_requested_duration_minutes",
            joinColumns = @JoinColumn(name = "matching_request_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_matching_requests_duration_minutes",
                    columnNames = {"matching_request_id", "duration_minutes"}
            )
    )
    @Column(name = "duration_minutes", nullable = false)
    private Set<Integer> requestedDurationMinutes = new LinkedHashSet<>();

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

    // 소비자 직접 중지 시 API 응답과 운영 추적에 사용할 취소 시각
    private Instant canceledAt;

    // 기본 무제한 탐색 요청 생성, 후보 없음만으로 실패시키지 않는 REQUESTED 시작
    public static MatchingRequest createUnlimitedSearch(
            Member member,
            Resort resort,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            Collection<Integer> requestedDurationMinutes,
            boolean isEquipmentReady
    ) {
        return create(
                member,
                resort,
                sport,
                lessonLevel,
                headcount,
                requestedDurationMinutes,
                isEquipmentReady,
                null
        );
    }

    // 매칭 요청 생성 시 DB REQUESTED 시작 및 fallback 탐색 만료 시각 선택 저장
    public static MatchingRequest create(
            Member member,
            Resort resort,
            Sport sport,
            LessonLevel lessonLevel,
            int headcount,
            Collection<Integer> requestedDurationMinutes,
            boolean isEquipmentReady,
            Instant expiresAt
    ) {
        MatchingRequest matchingRequest = new MatchingRequest();
        matchingRequest.member = member;
        matchingRequest.resort = resort;
        matchingRequest.sport = sport;
        matchingRequest.lessonLevel = lessonLevel;
        matchingRequest.headcount = headcount;
        matchingRequest.replaceRequestedDurationMinutes(requestedDurationMinutes);
        matchingRequest.isEquipmentReady = isEquipmentReady;
        matchingRequest.status = MatchingRequestStatus.REQUESTED;
        matchingRequest.expiresAt = expiresAt;
        return matchingRequest;
    }

    // 후보 조회와 API 응답에서 사용할 소비자 희망 시간 읽기 전용 view 반환
    public Set<Integer> getRequestedDurationMinutes() {
        return Collections.unmodifiableSet(requestedDurationMinutes);
    }

    // fallback 만료 시각이 있는 요청만 현재 시각 기준 SEARCHING 종료 여부 판단
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

    // 전체 결제 완료와 강습 생성 이후 요청 최종 확정 상태 이동
    public void confirm() {
        updateStatus(MatchingRequestStatus.CONFIRMED, null);
    }

    // 연결된 강습 완료 이후 매칭 요청 완료 상태 이동
    public void complete() {
        updateStatus(MatchingRequestStatus.COMPLETED, null);
    }

    // 소비자 직접 중지 요청의 취소 사유 저장 및 상태 조회 원인 구분
    public void cancelByConsumer() {
        cancelByConsumer(null);
    }

    // 소비자 직접 중지 요청의 취소 사유와 취소 시각 저장
    public void cancelByConsumer(Instant canceledAt) {
        this.canceledAt = canceledAt;
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

    // 강사 availableDurationMinutes와 교집합 비교할 희망 시간 목록 양수 검증
    private void replaceRequestedDurationMinutes(Collection<Integer> requestedDurationMinutes) {
        if (requestedDurationMinutes == null || requestedDurationMinutes.isEmpty()) {
            throw new IllegalArgumentException("requestedDurationMinutes must not be empty.");
        }

        // 같은 시간 중복 전송 시 하나의 선택값 저장을 위한 Set 정규화
        LinkedHashSet<Integer> nextRequestedDurationMinutes = new LinkedHashSet<>();
        for (Integer durationMinutes : requestedDurationMinutes) {
            if (durationMinutes == null || durationMinutes <= 0) {
                throw new IllegalArgumentException("requestedDurationMinutes must contain positive minutes.");
            }
            nextRequestedDurationMinutes.add(durationMinutes);
        }

        this.requestedDurationMinutes.clear();
        this.requestedDurationMinutes.addAll(nextRequestedDurationMinutes);
    }
}
