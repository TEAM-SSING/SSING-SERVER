package org.sopt.ssingserver.global.response;

import org.springframework.http.HttpStatus;

public interface SuccessCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
