package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;

@Schema(description = "강사 매칭 대기 화면 복구용 현재 제안 재확인 응답")
public record InstructorMatchingOffersResponse(

        @Schema(
                description = "홈 조회 이후 발견된 복구 가능한 실시간 제안 ID. 제안이 없으면 null",
                example = "21",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Long offerId,

        @Schema(
                description = "매칭 대기 화면 복구에 사용하는 강사의 저장 조건",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        MatchingSettingResponse matchingSetting
) {

    public static InstructorMatchingOffersResponse from(InstructorMatchingOffersResult result) {
        return new InstructorMatchingOffersResponse(
                result.offerId(),
                MatchingSettingResponse.from(result.matchingSetting())
        );
    }

    @Schema(name = "InstructorMatchingWaitingSetting", description = "강사의 저장된 실시간 매칭 대기 조건")
    public record MatchingSettingResponse(

            @JsonProperty("isExposed")
            @Schema(
                    description = "현재 실시간 매칭 대기열 노출 여부",
                    example = "true",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            boolean isExposed,

            @Schema(
                    description = "강사가 활동하는 리조트",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            ResortResponse resort,

            @Schema(
                    description = "저장된 강습 종목",
                    example = "SNOWBOARD",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            Sport sport,

            @Schema(
                    description = "저장된 강습 가능 레벨. 중복 없이 서버 enum 선언 순서로 반환",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            List<LessonLevel> lessonLevels,

            @Schema(
                    description = "저장된 강습 가능 시간(분). 중복 없이 숫자 오름차순으로 반환",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            List<Integer> availableDurationMinutes,

            @Schema(
                    description = "최대 강습 가능 인원",
                    example = "3",
                    minimum = "1",
                    maximum = "5",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            int maxHeadcount,

            @Schema(
                    description = "장비 준비 완료 여부",
                    example = "true",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            boolean equipmentReady
    ) {

        private static MatchingSettingResponse from(
                InstructorMatchingOffersResult.MatchingSettingResult result
        ) {
            return new MatchingSettingResponse(
                    result.isExposed(),
                    ResortResponse.from(result.resort()),
                    result.sport(),
                    result.lessonLevels(),
                    result.availableDurationMinutes(),
                    result.maxHeadcount(),
                    result.equipmentReady()
            );
        }
    }

    @Schema(name = "InstructorMatchingOfferRequestSummary", description = "강사 매칭 제안의 대표 요청자 요약")
    public record RequestSummaryResponse(

            @Schema(description = "그룹 첫 요청자의 닉네임", example = "홍길동")
            String requesterName,

            @Schema(description = "그룹 첫 요청의 인원", example = "2")
            int headcount,

            @Schema(description = "같은 매칭 그룹의 요청 수", example = "1")
            int matchingRequestCount
    ) {

        public static RequestSummaryResponse from(InstructorMatchingOffersResult.RequestSummaryResult result) {
            if (result == null) {
                return null;
            }

            return new RequestSummaryResponse(
                    result.requesterName(),
                    result.headcount(),
                    result.matchingRequestCount()
            );
        }
    }

    @Schema(description = "강습 조건 요약")
    public record LessonSummaryResponse(

            @Schema(description = "리조트 요약")
            ResortResponse resort,

            @Schema(description = "종목", example = "SNOWBOARD")
            Sport sport,

            @Schema(description = "강습 레벨", example = "FIRST_TIME")
            LessonLevel level,

            @Schema(description = "서버가 확정한 강습 시간(분)", example = "120")
            int durationMinutes,

            @Schema(description = "그룹 전체 인원", example = "3")
            int totalHeadcount,

            @Schema(description = "강습 시작 유형", example = "IMMEDIATE")
            String startType
    ) {

        public static LessonSummaryResponse from(InstructorMatchingOffersResult.LessonSummaryResult result) {
            if (result == null) {
                return null;
            }

            return new LessonSummaryResponse(
                    ResortResponse.from(result.resort()),
                    result.sport(),
                    result.level(),
                    result.durationMinutes(),
                    result.totalHeadcount(),
                    result.startType()
            );
        }
    }

    @Schema(description = "리조트 요약")
    public record ResortResponse(

            @Schema(
                    description = "서버 식별 코드",
                    example = "HIGH1",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String code,

            @Schema(
                    description = "Android 표시 이름",
                    example = "하이원",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String displayName
    ) {

        public static ResortResponse from(InstructorMatchingOffersResult.ResortResult result) {
            if (result == null) {
                return null;
            }

            return new ResortResponse(result.code(), result.displayName());
        }
    }
}
