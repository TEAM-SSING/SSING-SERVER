package org.sopt.ssingserver.global.swagger.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

class ErrorResponseSchemaCustomizerTest {

    private final ErrorResponseSchemaCustomizer customizer = new ErrorResponseSchemaCustomizer();

    @Test
    void 공통과_validation_오류_schema를_component에_등록한다() {
        OpenAPI openAPI = new OpenAPI();

        customizer.customise(openAPI);

        assertThat(openAPI.getComponents().getSchemas())
                .containsKeys("CommonErrorResponse", "ValidationErrorResponse");
        Schema<?> commonErrorSchema = openAPI.getComponents().getSchemas().get("CommonErrorResponse");
        Schema<?> validationErrorSchema = openAPI.getComponents().getSchemas().get("ValidationErrorResponse");
        assertThat(commonErrorSchema.getProperties())
                .containsKeys("success", "code", "message", "requestId")
                .doesNotContainKey("data")
                .doesNotContainKey("errors");
        assertThat(validationErrorSchema.getProperties())
                .containsKeys("success", "code", "message", "errors", "requestId")
                .doesNotContainKey("data");
    }

    @Test
    void 기존_component_schema를_보존한다() {
        Schema<?> existingSchema = new Schema<>().description("기존 schema");
        OpenAPI openAPI = new OpenAPI();
        openAPI.components(new io.swagger.v3.oas.models.Components().addSchemas("Existing", existingSchema));

        customizer.customise(openAPI);

        assertThat(openAPI.getComponents().getSchemas().get("Existing"))
                .isSameAs(existingSchema);
    }
}
