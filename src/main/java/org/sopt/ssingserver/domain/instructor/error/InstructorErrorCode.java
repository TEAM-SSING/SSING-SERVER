package org.sopt.ssingserver.domain.instructor.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InstructorErrorCode implements ErrorCode {

    INSTRUCTOR_NOT_APPROVED(HttpStatus.FORBIDDEN, "승인되지 않은 강사입니다."),
    ACTIVE_LESSON_EXISTS(HttpStatus.CONFLICT, "현재 진행 중인 강습이 있어 즉시노출을 시작할 수 없습니다."),
    INSTRUCTOR_RESORT_NOT_SET(HttpStatus.NOT_FOUND, "활동 리조트가 등록되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
