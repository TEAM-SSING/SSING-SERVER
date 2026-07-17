package org.sopt.ssingserver.domain.instructor.dev.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.DevInstructorConfigurationRequest;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.global.error.BusinessValidationException;

class DevInstructorConfigurationPolicyTest {

    private final DevInstructorConfigurationPolicy policy = new DevInstructorConfigurationPolicy();

    @Test
    void 허용된_클릭선택값은_검증을_통과한다() {
        policy.validate(configuration(100_000, 20_000, List.of(120, 180)));
    }

    @Test
    void UI를_우회한_시간값은_서버에서_거절한다() {
        assertThatThrownBy(() -> policy.validate(configuration(
                100_000,
                20_000,
                List.of(60)
        )))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("요청 값 검증에 실패했습니다.");
    }

    @Test
    void UI를_우회한_가격단위는_서버에서_거절한다() {
        assertThatThrownBy(() -> policy.validate(configuration(
                102_000,
                20_000,
                List.of(120)
        )))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void 중복_레벨과_시간은_서버에서_거절한다() {
        DevInstructorConfigurationRequest duplicateLevels = new DevInstructorConfigurationRequest(
                "VIVALDI",
                Sport.SKI,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.FIRST_TIME),
                List.of(120),
                3,
                100_000,
                20_000
        );

        assertThatThrownBy(() -> policy.validate(duplicateLevels))
                .isInstanceOf(BusinessValidationException.class);
        assertThatThrownBy(() -> policy.validate(configuration(
                100_000,
                20_000,
                List.of(120, 120)
        )))
                .isInstanceOf(BusinessValidationException.class);
    }

    private DevInstructorConfigurationRequest configuration(
            int basePrice,
            int additionalPrice,
            List<Integer> durations
    ) {
        return new DevInstructorConfigurationRequest(
                "VIVALDI",
                Sport.SKI,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                durations,
                3,
                basePrice,
                additionalPrice
        );
    }
}
