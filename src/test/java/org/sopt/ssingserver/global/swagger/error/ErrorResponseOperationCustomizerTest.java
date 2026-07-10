package org.sopt.ssingserver.global.swagger.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.web.method.HandlerMethod;

class ErrorResponseOperationCustomizerTest {

    private final ErrorResponseOperationCustomizer customizer = new ErrorResponseOperationCustomizer(
            new ErrorCodeResolver()
    );

    @Test
    void 실제_status별로_오류를_묶고_code를_key로_named_example을_만든다() throws Exception {
        ApiResponse successResponse = new ApiResponse().description("성공");
        Operation operation = new Operation().responses(
                new ApiResponses().addApiResponse("200", successResponse)
        );

        customizer.customize(operation, handlerMethod(MixedBadRequestController.class));

        assertThat(operation.getResponses().get("200")).isSameAs(successResponse);

        MediaType badRequest = operation.getResponses().get("400")
                .getContent()
                .get("application/json");
        assertThat(badRequest.getSchema()).isInstanceOf(ComposedSchema.class);
        assertThat(((ComposedSchema) badRequest.getSchema()).getOneOf())
                .extracting(Schema::get$ref)
                .containsExactly(
                        "#/components/schemas/CommonErrorResponse",
                        "#/components/schemas/ValidationErrorResponse"
                );
        assertThat(badRequest.getExamples())
                .containsOnlyKeys("VALIDATION_FAILED", "BAD_REQUEST");
        assertThat(exampleValue(badRequest, "VALIDATION_FAILED"))
                .containsEntry("success", false)
                .containsEntry("code", "VALIDATION_FAILED")
                .containsKey("errors")
                .doesNotContainKey("data");
        assertThat(exampleValue(badRequest, "BAD_REQUEST"))
                .containsEntry("success", false)
                .containsEntry("code", "BAD_REQUEST")
                .doesNotContainKey("errors")
                .doesNotContainKey("data");

        MediaType internalError = operation.getResponses().get("500")
                .getContent()
                .get("application/json");
        assertThat(internalError.getSchema().get$ref())
                .isEqualTo("#/components/schemas/CommonErrorResponse");
        assertThat(internalError.getExamples()).containsOnlyKeys("INTERNAL_ERROR");
    }

    @Test
    void 기존_오류_response의_description과_header는_보존한다() throws Exception {
        ApiResponse existing = new ApiResponse()
                .description("기존 설명")
                .addHeaderObject("X-Test", new io.swagger.v3.oas.models.headers.Header().description("기존 헤더"));
        Operation operation = new Operation().responses(
                new ApiResponses().addApiResponse("500", existing)
        );

        customizer.customize(operation, handlerMethod(InternalErrorController.class));

        assertThat(operation.getResponses().get("500")).isSameAs(existing);
        assertThat(existing.getDescription()).isEqualTo("기존 설명");
        assertThat(existing.getHeaders()).containsKey("X-Test");
        assertThat(existing.getContent().get("application/json").getExamples())
                .containsOnlyKeys("INTERNAL_ERROR");
    }

    @Test
    void 기존_201과_204_success_response를_수정하지_않는다() throws Exception {
        ApiResponse created = new ApiResponse().description("생성 성공");
        ApiResponse noContent = new ApiResponse().description("본문 없는 성공");
        Operation operation = new Operation().responses(
                new ApiResponses()
                        .addApiResponse("201", created)
                        .addApiResponse("204", noContent)
        );

        customizer.customize(operation, handlerMethod(InternalErrorController.class));

        assertThat(operation.getResponses().get("201")).isSameAs(created);
        assertThat(operation.getResponses().get("204")).isSameAs(noContent);
        assertThat(operation.getResponses().get("204").getContent()).isNull();
    }

    @Test
    void 오류_선언이_없으면_operation을_그대로_반환한다() throws Exception {
        ApiResponses responses = new ApiResponses().addApiResponse(
                "200",
                new ApiResponse().description("성공")
        );
        Operation operation = new Operation().responses(responses);

        Operation customized = customizer.customize(operation, handlerMethod(NoErrorDeclarationController.class));

        assertThat(customized).isSameAs(operation);
        assertThat(customized.getResponses()).isSameAs(responses);
        assertThat(customized.getResponses()).containsOnlyKeys("200");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exampleValue(MediaType mediaType, String code) {
        Example example = mediaType.getExamples().get(code);
        return (Map<String, Object>) example.getValue();
    }

    private HandlerMethod handlerMethod(Class<?> controllerType) throws Exception {
        Object controller = controllerType.getDeclaredConstructor().newInstance();
        Method method = controllerType.getMethod("handle");
        return new HandlerMethod(controller, method);
    }

    private static final class MixedBadRequestController {

        @ApiErrorCodes(
                type = CommonErrorCode.class,
                names = {"VALIDATION_FAILED", "BAD_REQUEST", "INTERNAL_ERROR"}
        )
        public void handle() {
        }
    }

    private static final class InternalErrorController {

        @ApiErrorCodes(type = CommonErrorCode.class, names = "INTERNAL_ERROR")
        public void handle() {
        }
    }

    private static final class NoErrorDeclarationController {

        public void handle() {
        }
    }
}
