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

    @Test
    void 자격증_종류는_선택_가능한_스키_또는_스노보드_종목으로_매핑된다() {
        assertThat(Arrays.asList(
                InstructorCertificateType.KSIA_SKI_LEVEL_1,
                InstructorCertificateType.KSIA_SKI_LEVEL_2,
                InstructorCertificateType.KSIA_SKI_LEVEL_3,
                InstructorCertificateType.SBAK_SKI_TEACHING_1,
                InstructorCertificateType.SBAK_SKI_TEACHING_2,
                InstructorCertificateType.SBAK_SKI_TEACHING_3
        )).allSatisfy(certificateType -> assertThat(certificateType.sport()).isSameAs(Sport.SKI));

        assertThat(Arrays.asList(
                InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_1,
                InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_2,
                InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_3,
                InstructorCertificateType.SBAK_SNOWBOARD_TEACHING_1,
                InstructorCertificateType.SBAK_SNOWBOARD_TEACHING_2,
                InstructorCertificateType.SBAK_SNOWBOARD_TEACHING_3
        )).allSatisfy(certificateType -> assertThat(certificateType.sport()).isSameAs(Sport.SNOWBOARD));
    }
}
