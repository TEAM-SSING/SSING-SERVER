package org.sopt.ssingserver.domain.instructor.dev.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;

@Schema(description = "개발용 강사 승인·매칭 설정")
public record DevInstructorConfigurationRequest(
        @NotBlank(message = "resortCode는 필수입니다.")
        @Schema(description = "DB에 존재하는 활동 리조트 코드", example = "VIVALDI_PARK")
        String resortCode,

        @NotNull(message = "sport는 필수입니다.")
        @Schema(description = "강습 종목: SKI 또는 SNOWBOARD", example = "SKI")
        Sport sport,

        @NotEmpty(message = "lessonLevels는 하나 이상 선택해야 합니다.")
        @Schema(
                description = "가능 강습 레벨. FIRST_TIME, BEGINNER, INTERMEDIATE, CERTIFIED 중 하나 이상이며 중복 불가",
                example = "[\"FIRST_TIME\", \"BEGINNER\"]"
        )
        List<@NotNull LessonLevel> lessonLevels,

        @NotEmpty(message = "availableDurationMinutes는 하나 이상 선택해야 합니다.")
        @Schema(description = "가능 시간. 120, 180, 240분 중 하나 이상이며 중복 불가", example = "[120, 180]")
        List<@NotNull Integer> availableDurationMinutes,

        @Min(value = 1, message = "maxHeadcount는 1명 이상이어야 합니다.")
        @Max(value = 5, message = "maxHeadcount는 5명 이하여야 합니다.")
        @Schema(description = "최대 강습 인원, 1~5명", example = "3")
        int maxHeadcount,

        @Min(value = 50_000, message = "basePriceAmount는 50000원 이상이어야 합니다.")
        @Max(value = 200_000, message = "basePriceAmount는 200000원 이하여야 합니다.")
        @Schema(description = "기본 2시간 가격, 50000~200000원·5000원 단위", example = "100000")
        int basePriceAmount,

        @Min(value = 0, message = "additionalPersonPriceAmount는 0원 이상이어야 합니다.")
        @Max(value = 50_000, message = "additionalPersonPriceAmount는 50000원 이하여야 합니다.")
        @Schema(description = "추가 1인 가격, 0~50000원·5000원 단위", example = "20000")
        int additionalPersonPriceAmount
) {
}
