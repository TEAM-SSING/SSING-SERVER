package org.sopt.ssingserver.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "matching_request_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequestGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MatchingRequestGroupStatus status;

    // 요청/강사 가능 시간 교집합에서 서버가 확정한 단일 강습 시간
    @Column(nullable = false)
    private int durationMinutes;

    public static MatchingRequestGroup createCandidate(int durationMinutes) {
        validateDurationMinutes(durationMinutes);

        MatchingRequestGroup matchingRequestGroup = new MatchingRequestGroup();
        matchingRequestGroup.status = MatchingRequestGroupStatus.CANDIDATE;
        matchingRequestGroup.durationMinutes = durationMinutes;
        return matchingRequestGroup;
    }

    public void expose() {
        this.status = MatchingRequestGroupStatus.EXPOSED;
    }

    public void markInstructorAccepted() {
        this.status = MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED;
    }

    public void markConsumerAccepted() {
        this.status = MatchingRequestGroupStatus.CONSUMER_ACCEPTED;
    }

    public void markPaymentPending() {
        this.status = MatchingRequestGroupStatus.PAYMENT_PENDING;
    }

    public void markPaymentExpired() {
        this.status = MatchingRequestGroupStatus.PAYMENT_EXPIRED;
    }

    public void confirm() {
        this.status = MatchingRequestGroupStatus.CONFIRMED;
    }

    public void lose() {
        this.status = MatchingRequestGroupStatus.LOST;
    }

    public void cancel() {
        this.status = MatchingRequestGroupStatus.CANCELED;
    }

    public void expire() {
        this.status = MatchingRequestGroupStatus.EXPIRED;
    }

    // 그룹 생성 이후 API 응답/결제/수업 생성에서 재사용할 확정 강습 시간 양수 검증
    private static void validateDurationMinutes(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive.");
        }
    }
}
