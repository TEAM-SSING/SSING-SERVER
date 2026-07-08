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
    MATCHING_RESORT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리조트를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
