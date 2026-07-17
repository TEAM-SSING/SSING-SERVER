package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;

@Schema(description = "개발용 매칭 상태 동작 실행 결과와 재조회 결과")
public record DevMatchingActionExecutionResponse(
        @Schema(description = "실행한 동작 key", example = "INSTRUCTOR_ACCEPT")
        DevMatchingActionKey actionKey,
        @Schema(description = "서버가 현재 관계에서 찾은 실제 동작 수행자")
        DevMatchingPersonResponse actor,
        @Schema(description = "실행 직전 상세")
        DevMatchingRequestDetailResponse before,
        @Schema(description = "실행 완료 뒤 다시 조회한 상세")
        DevMatchingRequestDetailResponse after
) {
}
