package org.sopt.ssingserver.domain.instructor.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InstructorSuccessCode implements SuccessCode {

    INSTRUCTOR_MATCHING_EXPOSURE_STARTED(HttpStatus.OK, "즉시 매칭 노출이 시작되었습니다."),
    INSTRUCTOR_MATCHING_EXPOSURE_CANCELLED(HttpStatus.OK, "즉시 매칭 노출이 중단되었습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
