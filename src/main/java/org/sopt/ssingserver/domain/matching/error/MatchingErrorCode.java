package org.sopt.ssingserver.domain.matching.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MatchingErrorCode implements ErrorCode {

    // 매칭 요청 소유 회원을 요청 저장 전에 찾지 못한 경우의 명시적 실패
    MATCHING_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 회원을 찾을 수 없습니다."),
    // 매칭 요청 대상 리조트를 요청 저장 전에 찾지 못한 경우의 명시적 실패
    MATCHING_RESORT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리조트를 찾을 수 없습니다."),
    // 소비자 상태 조회 대상 매칭 요청 row를 찾지 못한 경우의 명시적 실패
    MATCHING_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매칭 요청입니다."),
    // 이미 종료되었거나 MVP 범위 밖 결제 완료 요청을 소비자 중지 API로 취소하려는 경우
    MATCHING_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 없는 매칭 요청 상태입니다."),
    MATCHING_OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매칭 제안입니다."),
    MATCHING_OFFER_ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답한 매칭 제안입니다."),
    MATCHING_PRICE_POLICY_NOT_FOUND(HttpStatus.CONFLICT, "매칭 가격 정책을 찾을 수 없습니다."),
    MATCHING_REQUEST_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "최종 응답을 반영할 수 없는 매칭 요청 상태입니다."),
    MATCHING_CONFIRMATION_EXPIRED(HttpStatus.CONFLICT, "최종 응답 가능 시간이 만료되었습니다."),
    MATCHING_PAYMENT_NOT_PENDING(HttpStatus.CONFLICT, "결제 대기 상태가 아닌 매칭 요청입니다."),
    MATCHING_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 만료되었습니다."),
    MATCHING_GROUP_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 종료된 매칭 그룹입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
