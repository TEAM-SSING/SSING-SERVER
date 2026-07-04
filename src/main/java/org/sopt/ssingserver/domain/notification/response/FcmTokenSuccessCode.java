package org.sopt.ssingserver.domain.notification.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FcmTokenSuccessCode implements SuccessCode {

    FCM_TOKEN_REGISTERED_OR_UPDATED(HttpStatus.NO_CONTENT, "FCM token 등록 또는 수정에 성공했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
