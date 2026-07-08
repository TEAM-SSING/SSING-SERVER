package org.sopt.ssingserver.domain.payment.repository;

import java.util.Optional;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.springframework.data.jpa.repository.JpaRepository;

// 소비자 매칭 상태 조회에서 요청별 최신 결제 진행 상태 확인용 Repository
public interface MatchingRequestPaymentRepository extends JpaRepository<MatchingRequestPayment, Long> {

    Optional<MatchingRequestPayment> findFirstByMatchingRequestIdOrderByIdDesc(Long matchingRequestId);
}
