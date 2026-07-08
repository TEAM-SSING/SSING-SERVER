package org.sopt.ssingserver.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValidRequestedDurationsTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 전달받은_허용값으로_검증한다() {
        DurationRequest request = new DurationRequest(List.of(30, 60));

        Set<ConstraintViolation<DurationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 전달받은_허용값에_없는_값이면_사용처_메시지로_검증에_실패한다() {
        DurationRequest request = new DurationRequest(List.of(120));

        Set<ConstraintViolation<DurationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("테스트 시간은 30, 60분 중에서 선택해야 합니다.");
    }

    @Test
    void 중복된_값이면_사용처_메시지로_검증에_실패한다() {
        DurationRequest request = new DurationRequest(List.of(30, 30));

        Set<ConstraintViolation<DurationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("테스트 시간은 중복 없이 선택해야 합니다.");
    }

    @Test
    void null_empty_null_element는_다른_검증_책임으로_위임한다() {
        DurationRequest nullRequest = new DurationRequest(null);
        DurationRequest emptyRequest = new DurationRequest(List.of());
        DurationRequest nullElementRequest = new DurationRequest(listWithNullElement());

        Set<ConstraintViolation<DurationRequest>> nullViolations = validator.validate(nullRequest);
        Set<ConstraintViolation<DurationRequest>> emptyViolations = validator.validate(emptyRequest);
        Set<ConstraintViolation<DurationRequest>> nullElementViolations = validator.validate(nullElementRequest);

        assertThat(nullViolations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("테스트 시간은 1개 이상 선택해야 합니다.");
        assertThat(emptyViolations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("테스트 시간은 1개 이상 선택해야 합니다.");
        assertThat(nullElementViolations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("테스트 시간은 null일 수 없습니다.");
    }

    private record DurationRequest(
            @NotEmpty(message = "테스트 시간은 1개 이상 선택해야 합니다.")
            @ValidRequestedDurations(
                    allowedValues = {30, 60},
                    notAllowedMessage = "테스트 시간은 30, 60분 중에서 선택해야 합니다.",
                    duplicatedMessage = "테스트 시간은 중복 없이 선택해야 합니다."
            )
            List<@NotNull(message = "테스트 시간은 null일 수 없습니다.") Integer> durationMinutes
    ) {
    }

    private List<Integer> listWithNullElement() {
        List<Integer> durationMinutes = new ArrayList<>();
        durationMinutes.add(30);
        durationMinutes.add(null);
        return durationMinutes;
    }
}
