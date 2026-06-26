package org.sopt.ssingserver.global.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.sopt.ssingserver.global.logging.TraceIdFilter;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DEFAULT_VALIDATION_MESSAGE = "요청 값이 올바르지 않습니다.";
    private static final String REQUIRED_PARAMETER_MESSAGE = "필수 요청 파라미터가 누락되었습니다.";

    // 비즈니스 규칙 위반
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(BaseResponse.error(errorCode, resolveTraceId(request)));
    }

    // @Valid @RequestBody DTO의 필드 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = resolveFieldErrors(exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(CommonErrorCode.VALIDATION_FAILED, errors, resolveTraceId(request)));
    }

    // 컨트롤러 메서드 파라미터(@RequestParam, @PathVariable) 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<BaseResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = resolveParameterErrors(exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(CommonErrorCode.VALIDATION_FAILED, errors, resolveTraceId(request)));
    }

    // 요청 바디 JSON 파싱 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(CommonErrorCode.BAD_REQUEST, resolveTraceId(request)));
    }

    // 필수 @RequestParam 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = Map.of(exception.getParameterName(), REQUIRED_PARAMETER_MESSAGE);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(CommonErrorCode.VALIDATION_FAILED, errors, resolveTraceId(request)));
    }

    // 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(CommonErrorCode.BAD_REQUEST, resolveTraceId(request)));
    }

    // 허용되지 않은 HTTP 메서드 요청
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(BaseResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED, resolveTraceId(request)));
    }

    // 매핑되지 않는 경로 요청
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BaseResponse.error(CommonErrorCode.NOT_FOUND, resolveTraceId(request)));
    }

    // 그 외 처리되지 않은 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        String traceId = resolveTraceId(request);
        log.error("Unhandled exception. traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.error(CommonErrorCode.INTERNAL_ERROR, traceId));
    }

    private Map<String, String> resolveFieldErrors(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), resolveMessage(fieldError));
        }

        return errors;
    }

    private Map<String, String> resolveParameterErrors(HandlerMethodValidationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String parameterName = resolveParameterName(result.getMethodParameter());

            if (result instanceof ParameterErrors parameterErrors) {
                for (FieldError fieldError : parameterErrors.getFieldErrors()) {
                    errors.putIfAbsent(parameterName + "." + fieldError.getField(), resolveMessage(fieldError));
                }
                for (ObjectError globalError : parameterErrors.getGlobalErrors()) {
                    errors.putIfAbsent(parameterName, resolveMessage(globalError));
                }
                continue;
            }

            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.putIfAbsent(parameterName, resolveMessage(error));
            }
        }

        return errors;
    }

    private String resolveParameterName(MethodParameter methodParameter) {
        RequestParam requestParam = methodParameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (!requestParam.name().isBlank()) {
                return requestParam.name();
            }
            if (!requestParam.value().isBlank()) {
                return requestParam.value();
            }
        }

        PathVariable pathVariable = methodParameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            if (!pathVariable.name().isBlank()) {
                return pathVariable.name();
            }
            if (!pathVariable.value().isBlank()) {
                return pathVariable.value();
            }
        }

        String parameterName = methodParameter.getParameterName();
        return parameterName != null ? parameterName : "parameter";
    }

    private String resolveMessage(MessageSourceResolvable error) {
        String defaultMessage = error.getDefaultMessage();
        return defaultMessage != null ? defaultMessage : DEFAULT_VALIDATION_MESSAGE;
    }

    // TraceIdFilter가 요청 attribute에 심어둔 traceId 조회
    private String resolveTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return traceId != null ? traceId.toString() : null;
    }
}
