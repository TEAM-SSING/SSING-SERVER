package org.sopt.ssingserver.domain.lesson.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LessonErrorCode implements ErrorCode {

    LESSON_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 강습입니다."),
    LESSON_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 강습에 참여한 회원만 강습 상세 정보를 조회할 수 있습니다."),
    LESSON_PRICE_NOT_FOUND(HttpStatus.NOT_FOUND, "강습 가격 정보를 찾을 수 없습니다."),
    LESSON_CANCELLATION_NOT_FOUND(HttpStatus.NOT_FOUND, "강습 취소 정보를 찾을 수 없습니다."),
    LESSON_INVALID_STATE(HttpStatus.CONFLICT, "현재 강습 상태와 필요한 강습 상세 정보가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
