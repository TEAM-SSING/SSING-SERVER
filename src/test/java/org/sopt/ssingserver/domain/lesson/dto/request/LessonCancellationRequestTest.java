package org.sopt.ssingserver.domain.lesson.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.lesson.enums.LessonCancelReason;

class LessonCancellationRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 일정_변경_요청은_검증을_통과한다() {
        LessonCancellationRequest request = new LessonCancellationRequest(
                LessonCancelReason.SCHEDULE_CHANGED,
                null
        );

        Set<ConstraintViolation<LessonCancellationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 강사를_못_만난_요청은_검증을_통과한다() {
        LessonCancellationRequest request = new LessonCancellationRequest(
                LessonCancelReason.INSTRUCTOR_NOT_MET,
                null
        );

        Set<ConstraintViolation<LessonCancellationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 기타_요청은_상세_사유가_있으면_검증을_통과한다() {
        LessonCancellationRequest request = new LessonCancellationRequest(
                LessonCancelReason.ETC,
                " 개인 사정으로 강습을 취소합니다. "
        );

        Set<ConstraintViolation<LessonCancellationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 취소_사유가_null이면_검증에_실패한다() {
        LessonCancellationRequest request = new LessonCancellationRequest(null, null);

        Set<ConstraintViolation<LessonCancellationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("취소 사유는 필수입니다.");
    }

    @Test
    void 기타인데_상세_사유가_비어있으면_검증에_실패한다() {
        LessonCancellationRequest request = new LessonCancellationRequest(LessonCancelReason.ETC, " ");

        Set<ConstraintViolation<LessonCancellationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("기타 취소 사유를 입력해주세요.");
    }

    @Test
    void 기타가_아닌데_상세_사유가_있으면_검증에_실패한다() {
        LessonCancellationRequest request = new LessonCancellationRequest(
                LessonCancelReason.SCHEDULE_CHANGED,
                "상세 사유"
        );

        Set<ConstraintViolation<LessonCancellationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("기타를 선택한 경우에만 상세 취소 사유를 입력할 수 있습니다.");
    }
}
