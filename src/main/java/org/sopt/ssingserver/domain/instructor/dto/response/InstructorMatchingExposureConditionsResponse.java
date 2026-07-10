package org.sopt.ssingserver.domain.instructor.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.dto.result.InstructorMatchingExposureConditionsResult;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "InstructorMatchingExposureConditionsResponse",
        description = "강사 즉시노출 조건 화면 초기 데이터"
)
public record InstructorMatchingExposureConditionsResponse(
        @Schema(
                description = "강사 프로필에 등록된 활동 리조트",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        ResortResponse resort,

        @Schema(
                description = "등록된 자격증 종목을 중복 제거한 선택 가능 종목 목록",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<Sport> availableSports,

        @Schema(
                description = "선택 가능한 강습 시간 옵션. 분 단위",
                example = "[120, 180, 240]",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<Integer> durationOptions,

        @Schema(
                description = "저장된 즉시노출 조건. 저장값이 없으면 필드 생략",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        CurrentSettingResponse currentSetting
) {

    public static InstructorMatchingExposureConditionsResponse from(
            InstructorMatchingExposureConditionsResult result
    ) {
        return new InstructorMatchingExposureConditionsResponse(
                ResortResponse.from(result.resort()),
                result.availableSports(),
                result.durationOptions(),
                CurrentSettingResponse.from(result.currentSetting())
        );
    }

    @Schema(
            name = "InstructorMatchingExposureConditionsResort",
            description = "즉시노출 활동 리조트"
    )
    public record ResortResponse(
            @Schema(
                    description = "리조트 코드",
                    example = "HIGH1",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String code,

            @Schema(
                    description = "Android 표시용 리조트 이름",
                    example = "하이원",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String displayName
    ) {

        private static ResortResponse from(
                InstructorMatchingExposureConditionsResult.ResortResult result
        ) {
            return new ResortResponse(result.code(), result.displayName());
        }
    }

    @Schema(
            name = "InstructorMatchingExposureCurrentSetting",
            description = "현재 저장된 즉시노출 조건"
    )
    public record CurrentSettingResponse(
            @Schema(
                    description = "현재 노출 종목",
                    example = "SNOWBOARD",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            Sport sport,

            @Schema(
                    description = "강습 가능한 수강생 레벨 목록",
                    example = "[\"FIRST_TIME\", \"BEGINNER\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            List<LessonLevel> lessonLevels,

            @Schema(
                    description = "최대 강습 가능 인원",
                    example = "3",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            int maxHeadcount,

            @Schema(
                    description = "장비 착용 및 즉시 이동 가능 여부",
                    example = "true",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            boolean equipmentReady,

            @Schema(
                    description = "강습 가능 시간 목록. 분 단위",
                    example = "[120, 180, 240]",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            List<Integer> availableDurationMinutes,

            @Schema(
                    description = "현재 즉시노출 여부",
                    example = "true",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            boolean isExposed
    ) {

        private static CurrentSettingResponse from(
                InstructorMatchingExposureConditionsResult.CurrentSettingResult result
        ) {
            if (result == null) {
                // 상위 NON_NULL 규칙에 따라 저장된 조건이 없을 때 currentSetting 필드 자체를 생략한다.
                return null;
            }
            return new CurrentSettingResponse(
                    result.sport(),
                    result.lessonLevels(),
                    result.maxHeadcount(),
                    result.equipmentReady(),
                    result.availableDurationMinutes(),
                    result.isExposed()
            );
        }
    }
}
