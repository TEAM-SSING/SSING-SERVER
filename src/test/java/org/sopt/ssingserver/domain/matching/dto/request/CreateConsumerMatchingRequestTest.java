package org.sopt.ssingserver.domain.matching.dto.request;

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
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.member.enums.Gender;

class CreateConsumerMatchingRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 유효한_요청은_검증을_통과한다() {
        CreateConsumerMatchingRequest request = request(List.of(120, 180));

        Set<ConstraintViolation<CreateConsumerMatchingRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 희망_강습_시간에_허용되지_않은_값이_있으면_검증에_실패한다() {
        CreateConsumerMatchingRequest request = request(List.of(90));

        Set<ConstraintViolation<CreateConsumerMatchingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("희망 강습 시간은 120, 180, 240분 중에서 선택해야 합니다.");
    }

    @Test
    void 희망_강습_시간에_중복된_값이_있으면_검증에_실패한다() {
        CreateConsumerMatchingRequest request = request(List.of(120, 120));

        Set<ConstraintViolation<CreateConsumerMatchingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("희망 강습 시간은 중복 없이 선택해야 합니다.");
    }

    @Test
    void 희망_강습_시간이_비어있으면_형식_검증에서만_실패하고_커스텀_검증은_통과시킨다() {
        CreateConsumerMatchingRequest request = request(List.of());

        Set<ConstraintViolation<CreateConsumerMatchingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("희망 강습 시간은 1개 이상 선택해야 합니다.");
    }

    @Test
    void 참여자_목록이_비어있으면_검증에_실패한다() {
        CreateConsumerMatchingRequest request = new CreateConsumerMatchingRequest(
                "HIGH1",
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                List.of(120),
                List.of(),
                true
        );

        Set<ConstraintViolation<CreateConsumerMatchingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("참여자는 1명 이상이어야 합니다.");
    }

    @Test
    void 참여자_나이가_1세_미만이면_검증에_실패한다() {
        CreateConsumerMatchingRequest request = new CreateConsumerMatchingRequest(
                "HIGH1",
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                List.of(120),
                List.of(new CreateConsumerMatchingRequest.ParticipantRequest(0, Gender.FEMALE)),
                true
        );

        Set<ConstraintViolation<CreateConsumerMatchingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("참여자 나이는 1세 이상이어야 합니다.");
    }

    @Test
    void toCommand는_요청값과_참여자_목록을_매칭_생성_command로_변환한다() {
        CreateConsumerMatchingRequest request = request(List.of(120, 180));

        MatchingCreationCommand command = request.toCommand(1L);

        assertThat(command.memberId()).isEqualTo(1L);
        assertThat(command.resortCode()).isEqualTo("HIGH1");
        assertThat(command.sport()).isSameAs(Sport.SNOWBOARD);
        assertThat(command.lessonLevel()).isSameAs(LessonLevel.FIRST_TIME);
        assertThat(command.requestedDurationMinutes()).containsExactly(120, 180);
        assertThat(command.isEquipmentReady()).isTrue();
        assertThat(command.participants()).hasSize(2);
        assertThat(command.headcount()).isEqualTo(2);
        assertThat(command.participants())
                .extracting("age", "gender")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(24, Gender.FEMALE),
                        org.assertj.core.groups.Tuple.tuple(30, Gender.MALE)
                );
    }

    private CreateConsumerMatchingRequest request(List<Integer> requestedDurationMinutes) {
        return new CreateConsumerMatchingRequest(
                "HIGH1",
                Sport.SNOWBOARD,
                LessonLevel.FIRST_TIME,
                requestedDurationMinutes,
                List.of(
                        new CreateConsumerMatchingRequest.ParticipantRequest(24, Gender.FEMALE),
                        new CreateConsumerMatchingRequest.ParticipantRequest(30, Gender.MALE)
                ),
                true
        );
    }
}
