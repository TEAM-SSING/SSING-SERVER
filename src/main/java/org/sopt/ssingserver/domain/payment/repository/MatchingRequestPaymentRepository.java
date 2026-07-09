package org.sopt.ssingserver.domain.payment.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

// 소비자 매칭 상태 조회에서 요청별 최신 결제 진행 상태 확인용 Repository
public interface MatchingRequestPaymentRepository extends JpaRepository<MatchingRequestPayment, Long> {

    Optional<MatchingRequestPayment> findFirstByMatchingRequestIdOrderByIdDesc(Long matchingRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<MatchingRequestPayment> findFirstByMatchingRequestIdAndStatusOrderByIdDesc(
            Long matchingRequestId,
            MatchingRequestPaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select payment
            from MatchingRequestPayment payment
            where payment.matchingOffer.id = :matchingOfferId
            order by payment.id asc
            """)
    List<MatchingRequestPayment> findByMatchingOfferIdForUpdate(@Param("matchingOfferId") Long matchingOfferId);

    // 커밋 이후 WebSocket 결제 이벤트의 수신자와 요청별 결제 상태 조회
    @Query("""
            select payment
            from MatchingRequestPayment payment
            join fetch payment.matchingRequest matchingRequest
            join fetch matchingRequest.member
            where payment.matchingOffer.id = :matchingOfferId
            order by payment.id asc
            """)
    List<MatchingRequestPayment> findRealtimeContextByMatchingOfferIdOrderByIdAsc(
            @Param("matchingOfferId") Long matchingOfferId
    );
}
