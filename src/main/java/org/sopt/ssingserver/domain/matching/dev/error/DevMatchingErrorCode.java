package org.sopt.ssingserver.domain.matching.dev.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DevMatchingErrorCode implements ErrorCode {

    DEV_MATCHING_STATE_CHANGED(
            HttpStatus.CONFLICT,
            "조회한 뒤 매칭 상태가 바뀌었습니다. 최신 상태를 다시 확인해 주세요."
    ),
    DEV_MATCHING_ACTION_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "현재 상태에서 실행할 수 없는 개발용 매칭 동작입니다."
    );

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
