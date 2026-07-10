package org.sopt.ssingserver.domain.instructor.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.dto.result.InstructorMatchingExposureConditionsResult;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import tools.jackson.databind.ObjectMapper;

class InstructorMatchingExposureConditionsResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 조회_응답은_리조트와_강습_가능_종목만_포함한다() throws Exception {
        InstructorMatchingExposureConditionsResult result =
                new InstructorMatchingExposureConditionsResult(
                        new InstructorMatchingExposureConditionsResult.ResortResult("HIGH1", "하이원"),
                        List.of(Sport.SKI, Sport.SNOWBOARD)
                );

        String json = objectMapper.writeValueAsString(
                InstructorMatchingExposureConditionsResponse.from(result)
        );

        assertThat(json).contains("\"resort\":{\"code\":\"HIGH1\",\"displayName\":\"하이원\"}");
        assertThat(json).contains("\"availableSports\":[\"SKI\",\"SNOWBOARD\"]");
        assertThat(json).doesNotContain("durationOptions");
        assertThat(json).doesNotContain("currentSetting");
        assertThat(json).doesNotContain("availableCertificates");
    }
}
