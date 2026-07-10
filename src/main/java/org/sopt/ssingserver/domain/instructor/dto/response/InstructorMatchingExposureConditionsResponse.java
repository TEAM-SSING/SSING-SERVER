package org.sopt.ssingserver.domain.instructor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.dto.result.InstructorMatchingExposureConditionsResult;
import org.sopt.ssingserver.domain.instructor.enums.Sport;

@Schema(
        name = "InstructorMatchingExposureConditionsResponse",
        description = "강사 즉시노출 조건 화면의 리조트와 강습 가능 종목"
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
        List<Sport> availableSports
) {

    public static InstructorMatchingExposureConditionsResponse from(
            InstructorMatchingExposureConditionsResult result
    ) {
        return new InstructorMatchingExposureConditionsResponse(
                ResortResponse.from(result.resort()),
                result.availableSports()
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

}
