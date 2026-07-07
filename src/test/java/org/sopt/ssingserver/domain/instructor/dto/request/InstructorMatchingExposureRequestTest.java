package org.sopt.ssingserver.domain.instructor.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;

class InstructorMatchingExposureRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 유효한_요청은_검증을_통과한다() {
        InstructorMatchingExposureRequest request = request(List.of(120, 180, 240));

        Set<ConstraintViolation<InstructorMatchingExposureRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 강습_가능_시간에_허용되지_않은_값이_있으면_검증에_실패한다() {
        InstructorMatchingExposureRequest request = request(List.of(90));

        Set<ConstraintViolation<InstructorMatchingExposureRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("강습 가능 시간은 120, 180, 240분 중에서 선택해야 합니다.");
    }

    @Test
    void 강습_가능_시간에_중복된_값이_있으면_검증에_실패한다() {
        InstructorMatchingExposureRequest request = request(List.of(120, 120));

        Set<ConstraintViolation<InstructorMatchingExposureRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("강습 가능 시간은 중복 없이 선택해야 합니다.");
    }

    @Test
    void 강습_가능_시간이_비어있으면_형식_검증에서만_실패하고_커스텀_검증은_통과시킨다() {
        InstructorMatchingExposureRequest request = request(List.of());

        Set<ConstraintViolation<InstructorMatchingExposureRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("강습 가능 시간은 1개 이상 선택해야 합니다.");
    }

    @Test
    void 최대_강습_가능_인원이_범위를_벗어나면_검증에_실패한다() {
        InstructorMatchingExposureRequest request = new InstructorMatchingExposureRequest(
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME),
                List.of(120),
                6,
                true
        );

        Set<ConstraintViolation<InstructorMatchingExposureRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("최대 강습 가능 인원은 5명 이하여야 합니다.");
    }

    private InstructorMatchingExposureRequest request(List<Integer> availableDurationMinutes) {
        return new InstructorMatchingExposureRequest(
                Sport.SNOWBOARD,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                availableDurationMinutes,
                3,
                true
        );
    }
}
