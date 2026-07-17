package org.sopt.ssingserver.domain.matching.dev.service;

import java.util.List;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;

record DevMatchingActionContext(
        Long selectedRequestId,
        Long groupId,
        MatchingRequestGroupStatus groupStatus,
        Long offerId,
        MatchingOfferStatus offerStatus,
        DevMatchingPersonResponse instructor,
        List<RequestState> requests,
        List<PaymentState> payments
) {

    RequestState selectedRequest() {
        return requests.stream()
                .filter(request -> request.matchingRequestId().equals(selectedRequestId))
                .findFirst()
                .orElseThrow();
    }

    PaymentState selectedPayment() {
        return payments.stream()
                .filter(payment -> payment.matchingRequestId().equals(selectedRequestId))
                .findFirst()
                .orElse(null);
    }

    record RequestState(
            Long matchingRequestId,
            MatchingRequestStatus requestStatus,
            Long groupItemId,
            MatchingRequestGroupItemStatus groupItemStatus,
            MatchingStatus matchingStatus,
            DevMatchingPersonResponse consumer
    ) {
    }

    record PaymentState(
            Long paymentId,
            Long matchingRequestId,
            MatchingRequestPaymentStatus paymentStatus
    ) {
    }
}
