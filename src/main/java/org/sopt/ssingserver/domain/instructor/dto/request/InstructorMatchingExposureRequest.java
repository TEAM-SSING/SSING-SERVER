package org.sopt.ssingserver.domain.instructor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.global.validation.ValidRequestedDurations;

public record InstructorMatchingExposureRequest(
        @Schema(description = "노출할 종목", example = "SNOWBOARD")
        @NotNull(message = "노출할 종목은 필수입니다.")
        Sport sport,

        @Schema(description = "강습 가능한 레벨 목록", example = "[\"FIRST_TIME\", \"BEGINNER\"]")
        @NotEmpty(message = "강습 가능한 레벨은 1개 이상 선택해야 합니다.")
        List<@NotNull(message = "강습 가능한 레벨은 null일 수 없습니다.") LessonLevel> lessonLevels,

        @Schema(description = "강습 가능 시간 목록", example = "[120, 180, 240]")
        @NotEmpty(message = "강습 가능 시간은 1개 이상 선택해야 합니다.")
        @ValidRequestedDurations(
                allowedValues = {120, 180, 240},
                notAllowedMessage = "강습 가능 시간은 120, 180, 240분 중에서 선택해야 합니다.",
                duplicatedMessage = "강습 가능 시간은 중복 없이 선택해야 합니다."
        )
        List<@NotNull(message = "강습 가능 시간은 null일 수 없습니다.") Integer> availableDurationMinutes,

        @Schema(description = "최대 강습 가능 인원", example = "3")
        @NotNull(message = "최대 강습 가능 인원은 필수입니다.")
        @Min(value = 1, message = "최대 강습 가능 인원은 1명 이상이어야 합니다.")
        @Max(value = 5, message = "최대 강습 가능 인원은 5명 이하여야 합니다.")
        Integer maxHeadcount,

        @Schema(description = "장비 착용 및 즉시 이동 가능 여부", example = "true")
        @NotNull(message = "장비 착용 및 즉시 이동 가능 여부는 필수입니다.")
        @AssertTrue(message = "장비 착용 및 즉시 이동 가능 여부는 true여야 합니다.")
        Boolean equipmentReady
) {
}
