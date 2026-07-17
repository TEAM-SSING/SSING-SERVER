package org.sopt.ssingserver.domain.instructor.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InstructorMatchingExposureResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 매칭노출_시작응답은_예상가격과_원본_가격정책을_함께_반환한다() throws Exception {
        InstructorMatchingExposureResponse response = InstructorMatchingExposureResponse.of(
                true,
                87_500,
                60_000,
                20_000
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("isExposed").asBoolean()).isTrue();
        assertThat(json.path("estimatedLessonPriceAmount").asInt()).isEqualTo(87_500);
        assertThat(json.path("pricePolicy").path("baseDurationMinutes").asInt()).isEqualTo(120);
        assertThat(json.path("pricePolicy").path("basePriceAmount").asInt()).isEqualTo(60_000);
        assertThat(json.path("pricePolicy").path("additionalPersonPriceAmount").asInt()).isEqualTo(20_000);
        assertThat(json.size()).isEqualTo(3);
    }
}
