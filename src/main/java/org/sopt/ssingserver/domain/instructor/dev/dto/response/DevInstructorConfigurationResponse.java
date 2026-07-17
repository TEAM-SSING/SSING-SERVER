package org.sopt.ssingserver.domain.instructor.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;

@Schema(description = "실제 저장된 강사 매칭 설정과 가격")
public record DevInstructorConfigurationResponse(
        @Schema(description = "매칭 설정 ID. 설정이 없으면 값 없음")
        Long matchingSettingId,
        @Schema(description = "리조트 ID. 미설정이면 값 없음")
        Long resortId,
        @Schema(description = "리조트 코드. 미설정이면 값 없음")
        String resortCode,
        @Schema(description = "리조트 표시 이름. 미설정이면 값 없음")
        String resortDisplayName,
        @Schema(description = "강습 종목. 미설정이면 값 없음")
        Sport sport,
        @Schema(description = "가능 강습 레벨")
        List<LessonLevel> lessonLevels,
        @Schema(description = "가능 시간(분)")
        List<Integer> availableDurationMinutes,
        @Schema(description = "최대 인원. 미설정이면 값 없음")
        Integer maxHeadcount,
        @Schema(description = "장비 준비 여부. 테스트 설정은 항상 true")
        boolean equipmentReady,
        @Schema(description = "현재 새 매칭 후보 노출 여부")
        boolean exposed,
        @Schema(description = "현재 선택되는 활성 기본 가격. 없으면 값 없음")
        Integer basePriceAmount,
        @Schema(description = "현재 선택되는 활성 추가 인원 가격. 없으면 값 없음")
        Integer additionalPersonPriceAmount,
        @Schema(description = "활성 가격 정책 raw ID 목록. 둘 이상이면 설정 저장으로 정리 필요")
        List<Long> activePricePolicyIds,
        @Schema(description = "개발 콘솔에서 매칭 시작에 필요한 구성이 모두 유효한지")
        boolean complete
) {
}
