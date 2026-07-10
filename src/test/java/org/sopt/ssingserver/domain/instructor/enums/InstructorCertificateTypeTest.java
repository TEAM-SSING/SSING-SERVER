package org.sopt.ssingserver.domain.instructor.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class InstructorCertificateTypeTest {

    @Test
    void 자격증_종류는_DB에_저장할_12개_문자열값을_가진다() {
        assertThat(Arrays.stream(InstructorCertificateType.values())
                .map(Enum::name))
                .containsExactly(
                        "KSIA_SKI_LEVEL_1",
                        "KSIA_SKI_LEVEL_2",
                        "KSIA_SKI_LEVEL_3",
                        "KSIA_SNOWBOARD_LEVEL_1",
                        "KSIA_SNOWBOARD_LEVEL_2",
                        "KSIA_SNOWBOARD_LEVEL_3",
                        "SBAK_SKI_TEACHING_1",
                        "SBAK_SKI_TEACHING_2",
                        "SBAK_SKI_TEACHING_3",
                        "SBAK_SNOWBOARD_TEACHING_1",
                        "SBAK_SNOWBOARD_TEACHING_2",
                        "SBAK_SNOWBOARD_TEACHING_3"
                );
    }
}
