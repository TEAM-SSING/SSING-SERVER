package org.sopt.ssingserver.domain.instructor.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;

@Schema(description = "개발용 강사 동작 실행 전후 상태")
public record DevInstructorActionExecutionResponse(
        @Schema(description = "실행한 동작")
        DevInstructorActionKey actionKey,
        @Schema(description = "동작 직전 잠금·재검증한 상태")
        DevInstructorMemberResponse before,
        @Schema(description = "동작 반영 뒤 다시 읽은 상태")
        DevInstructorMemberResponse after
) {
}
