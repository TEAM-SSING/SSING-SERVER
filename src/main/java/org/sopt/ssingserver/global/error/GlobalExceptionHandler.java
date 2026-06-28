package org.sopt.ssingserver.global.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory errorResponseFactory;

    public GlobalExceptionHandler(ErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    // 비즈니스 규칙 위반
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return errorResponseFactory.error(errorCode, request);
    }

    // @Valid @RequestBody DTO의 필드 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return errorResponseFactory.validationError(ValidationErrorMapper.from(exception), request);
    }

    // 컨트롤러 메서드 파라미터(@RequestParam, @PathVariable) 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<BaseResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        return errorResponseFactory.validationError(ValidationErrorMapper.from(exception), request);
    }

    // 요청 바디 JSON 파싱 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return errorResponseFactory.error(CommonErrorCode.BAD_REQUEST, request);
    }

    // 필수 @RequestParam 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return errorResponseFactory.validationError(ValidationErrorMapper.from(exception), request);
    }

    // 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return errorResponseFactory.error(CommonErrorCode.BAD_REQUEST, request);
    }

    // 허용되지 않은 HTTP 메서드 요청
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return errorResponseFactory.error(CommonErrorCode.METHOD_NOT_ALLOWED, request);
    }

    // 매핑되지 않는 경로 요청
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponseFactory.error(CommonErrorCode.NOT_FOUND, request);
    }

    // 그 외 처리되지 않은 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        String traceId = errorResponseFactory.resolveTraceId(request);
        log.error("Unhandled exception. traceId={}", traceId, exception);
        return errorResponseFactory.error(CommonErrorCode.INTERNAL_ERROR, request);
    }
}
