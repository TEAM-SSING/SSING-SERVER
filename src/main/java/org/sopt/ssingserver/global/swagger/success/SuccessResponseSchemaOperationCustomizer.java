package org.sopt.ssingserver.global.swagger.success;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Optional;
import org.springdoc.core.customizers.OperationCustomizer;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.core.ResolvableType;
import org.springframework.web.method.HandlerMethod;

public class SuccessResponseSchemaOperationCustomizer implements OperationCustomizer {

    private static final String NO_CONTENT = "204";
    private static final String APPLICATION_JSON_MEDIA_TYPE = "application/json";
    private static final String COMPONENT_SCHEMA_PREFIX = "#/components/schemas/";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Optional<ResolvableType> dataType = resolveBaseResponseDataType(handlerMethod);
        if (dataType.isEmpty()) {
            return operation;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            return operation;
        }

        Schema<?> successSchema = createSuccessSchema(dataType.get());
        responses.forEach((responseCode, response) -> {
            if (isDocumentedSuccessResponse(responseCode)) {
                resolveApplicationJsonMediaType(response).setSchema(successSchema);
            }
        });
        return operation;
    }

    private Optional<ResolvableType> resolveBaseResponseDataType(HandlerMethod handlerMethod) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
        if (returnType.resolve() == null) {
            return Optional.empty();
        }

        ResolvableType bodyType = returnType;
        if (ResponseTypeNames.RESPONSE_ENTITY.equals(returnType.resolve().getName())) {
            bodyType = returnType.getGeneric(0);
        }

        if (bodyType.resolve() != BaseResponse.class) {
            return Optional.empty();
        }
        return Optional.of(bodyType.getGeneric(0));
    }

    private boolean isDocumentedSuccessResponse(String responseCode) {
        return responseCode != null
                && responseCode.length() == 3
                && responseCode.charAt(0) == '2'
                && !NO_CONTENT.equals(responseCode);
    }

    private MediaType resolveApplicationJsonMediaType(ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        MediaType applicationJson = content.get(APPLICATION_JSON_MEDIA_TYPE);
        if (applicationJson == null) {
            applicationJson = new MediaType();
            content.addMediaType(APPLICATION_JSON_MEDIA_TYPE, applicationJson);
        }
        content.remove("*/*");
        return applicationJson;
    }

    private Schema<?> createSuccessSchema(ResolvableType dataType) {
        Schema<?> schema = new ObjectSchema()
                .description("공통 성공 응답");
        schema.addProperty(
                "success",
                new BooleanSchema()
                        .description("요청 성공 여부")
                        .example(true)
        );
        schema.addProperty(
                "code",
                new StringSchema()
                        .description("API별 성공 응답 코드")
        );
        schema.addProperty(
                "message",
                new StringSchema()
                        .description("API별 성공 메시지")
        );

        Class<?> dataClass = dataType.resolve();
        if (dataClass != null && dataClass != Void.class) {
            schema.addProperty("data", schemaRef(dataClass));
        }

        schema.addRequiredItem("success");
        schema.addRequiredItem("code");
        schema.addRequiredItem("message");
        if (dataClass != null && dataClass != Void.class) {
            schema.addRequiredItem("data");
        }
        return schema;
    }

    private Schema<?> schemaRef(Class<?> schemaType) {
        return new Schema<>().$ref(COMPONENT_SCHEMA_PREFIX + schemaType.getSimpleName());
    }

    private static final class ResponseTypeNames {

        private static final String RESPONSE_ENTITY = "org.springframework.http.ResponseEntity";

        private ResponseTypeNames() {
        }
    }
}
