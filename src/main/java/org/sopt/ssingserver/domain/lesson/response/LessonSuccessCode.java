package org.sopt.ssingserver.domain.lesson.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LessonSuccessCode implements SuccessCode {

    LESSON_START_CONFIRMATION_PENDING(HttpStatus.OK, "강습 준비가 완료되었습니다."),
    LESSON_STARTED(HttpStatus.OK, "강습이 시작되었습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
