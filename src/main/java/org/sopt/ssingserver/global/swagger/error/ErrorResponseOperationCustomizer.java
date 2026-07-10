package org.sopt.ssingserver.global.swagger.error;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;

public class ErrorResponseOperationCustomizer implements OperationCustomizer {

    public static final String COMMON_ERROR_SCHEMA_REF = "#/components/schemas/CommonErrorResponse";
    public static final String VALIDATION_ERROR_SCHEMA_REF = "#/components/schemas/ValidationErrorResponse";

    private static final String APPLICATION_JSON = "application/json";
    private static final String REQUEST_ID_EXAMPLE = "req_abc123";

    private final ErrorCodeResolver errorCodeResolver;

    public ErrorResponseOperationCustomizer(ErrorCodeResolver errorCodeResolver) {
        this.errorCodeResolver = errorCodeResolver;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        List<ErrorCode> errorCodes = errorCodeResolver.resolve(handlerMethod);
        if (errorCodes.isEmpty()) {
            return operation;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        Map<HttpStatus, List<ErrorCode>> errorCodesByStatus = errorCodes.stream()
                .collect(Collectors.groupingBy(
                        ErrorCode::getStatus,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<HttpStatus, List<ErrorCode>> entry : errorCodesByStatus.entrySet()) {
            addErrorResponse(responses, entry.getKey(), entry.getValue());
        }
        return operation;
    }

    private void addErrorResponse(
            ApiResponses responses,
            HttpStatus status,
            List<ErrorCode> errorCodes
    ) {
        String responseCode = Integer.toString(status.value());
        ApiResponse response = responses.get(responseCode);
        if (response == null) {
            response = new ApiResponse().description(status.getReasonPhrase());
            responses.addApiResponse(responseCode, response);
        }

        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        MediaType mediaType = content.get(APPLICATION_JSON);
        if (mediaType == null) {
            mediaType = new MediaType();
            content.addMediaType(APPLICATION_JSON, mediaType);
        }
        mediaType.setSchema(resolveSchema(errorCodes));
        mediaType.setExamples(createExamples(errorCodes));
    }

    private Schema<?> resolveSchema(List<ErrorCode> errorCodes) {
        boolean hasValidationError = errorCodes.stream()
                .anyMatch(errorCode -> CommonErrorCode.VALIDATION_FAILED.getCode().equals(errorCode.getCode()));
        boolean hasGeneralError = errorCodes.stream()
                .anyMatch(errorCode -> !CommonErrorCode.VALIDATION_FAILED.getCode().equals(errorCode.getCode()));

        if (hasValidationError && hasGeneralError) {
            return new ComposedSchema()
                    .addOneOfItem(schemaRef(COMMON_ERROR_SCHEMA_REF))
                    .addOneOfItem(schemaRef(VALIDATION_ERROR_SCHEMA_REF));
        }
        if (hasValidationError) {
            return schemaRef(VALIDATION_ERROR_SCHEMA_REF);
        }
        return schemaRef(COMMON_ERROR_SCHEMA_REF);
    }

    private Schema<?> schemaRef(String ref) {
        return new Schema<>().$ref(ref);
    }

    private Map<String, Example> createExamples(List<ErrorCode> errorCodes) {
        Map<String, Example> examples = new LinkedHashMap<>();
        for (ErrorCode errorCode : errorCodes) {
            examples.put(
                    errorCode.getCode(),
                    new Example()
                            .summary(errorCode.getMessage())
                            .value(createExampleValue(errorCode))
            );
        }
        return examples;
    }

    private Map<String, Object> createExampleValue(ErrorCode errorCode) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("success", false);
        value.put("code", errorCode.getCode());
        value.put("message", errorCode.getMessage());
        if (CommonErrorCode.VALIDATION_FAILED.getCode().equals(errorCode.getCode())) {
            value.put("errors", Map.of("field", "요청 값을 확인해주세요."));
        }
        value.put("requestId", REQUEST_ID_EXAMPLE);
        return value;
    }
}
