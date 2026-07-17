package org.sopt.ssingserver.domain.instructor.dev.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DevInstructorErrorCode implements ErrorCode {

    DEV_INSTRUCTOR_MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "실제 카카오 로그인 회원을 찾을 수 없습니다."
    ),
    DEV_INSTRUCTOR_STATE_CHANGED(
            HttpStatus.CONFLICT,
            "조회한 뒤 회원 또는 강사 설정이 바뀌었습니다. 최신 상태를 다시 확인해 주세요."
    ),
    DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "현재 상태에서 실행할 수 없는 개발용 강사 동작입니다."
    );

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
