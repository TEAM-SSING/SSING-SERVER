package org.sopt.ssingserver.domain.matching.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MatchingSuccessCode implements SuccessCode {

    MATCHING_REQUEST_CREATED(HttpStatus.CREATED, "매칭 요청이 생성되었습니다."),
    MATCHING_REQUEST_CANCELED(HttpStatus.OK, "매칭 요청이 취소되었습니다."),
    MATCHING_OFFER_RESPONDED(HttpStatus.OK, "매칭 제안 응답이 반영되었습니다."),
    MATCHING_CONFIRMATION_UPDATED(HttpStatus.OK, "매칭 응답이 반영되었습니다."),
    MATCHING_PAYMENT_COMPLETED(HttpStatus.OK, "결제가 완료되었습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
