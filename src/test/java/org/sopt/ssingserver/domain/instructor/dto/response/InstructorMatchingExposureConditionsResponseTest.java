package org.sopt.ssingserver.domain.instructor.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.dto.result.InstructorMatchingExposureConditionsResult;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import tools.jackson.databind.ObjectMapper;

class InstructorMatchingExposureConditionsResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 저장된_조건이_없으면_currentSetting을_JSON에서_제외한다() throws Exception {
        InstructorMatchingExposureConditionsResult result =
                new InstructorMatchingExposureConditionsResult(
                        new InstructorMatchingExposureConditionsResult.ResortResult("HIGH1", "하이원"),
                        List.of(),
                        List.of(120, 180, 240),
                        null
                );

        String json = objectMapper.writeValueAsString(
                InstructorMatchingExposureConditionsResponse.from(result)
        );

        assertThat(json).contains("\"availableSports\":[]");
        assertThat(json).contains("\"durationOptions\":[120,180,240]");
        assertThat(json).doesNotContain("currentSetting");
        assertThat(json).doesNotContain("availableCertificates");
    }

    @Test
    void 저장된_조건이_있으면_화면_복원에_필요한_필드를_매핑한다() {
        InstructorMatchingExposureConditionsResult result =
                new InstructorMatchingExposureConditionsResult(
                        new InstructorMatchingExposureConditionsResult.ResortResult("HIGH1", "하이원"),
                        List.of(Sport.SKI, Sport.SNOWBOARD),
                        List.of(120, 180, 240),
                        new InstructorMatchingExposureConditionsResult.CurrentSettingResult(
                                Sport.SNOWBOARD,
                                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                                3,
                                true,
                                List.of(120, 180),
                                true
                        )
                );

        InstructorMatchingExposureConditionsResponse response =
                InstructorMatchingExposureConditionsResponse.from(result);

        assertThat(response.availableSports()).containsExactly(Sport.SKI, Sport.SNOWBOARD);
        assertThat(response.currentSetting().sport()).isSameAs(Sport.SNOWBOARD);
        assertThat(response.currentSetting().lessonLevels())
                .containsExactly(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER);
        assertThat(response.currentSetting().availableDurationMinutes()).containsExactly(120, 180);
        assertThat(response.currentSetting().maxHeadcount()).isEqualTo(3);
        assertThat(response.currentSetting().equipmentReady()).isTrue();
        assertThat(response.currentSetting().isExposed()).isTrue();
    }
}
