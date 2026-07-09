package org.sopt.ssingserver.domain.home.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HomeErrorCode implements ErrorCode {

    INSTRUCTOR_HOME_UNSUPPORTED_DISPLAY_STATUS(HttpStatus.INTERNAL_SERVER_ERROR, "강사 홈에서 지원하지 않는 매칭 카드 상태입니다."),
    INSTRUCTOR_HOME_GROUP_ITEM_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "매칭 그룹 항목을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
