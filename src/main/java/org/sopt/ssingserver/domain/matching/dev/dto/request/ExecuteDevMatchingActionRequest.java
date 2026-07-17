package org.sopt.ssingserver.domain.matching.dev.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;

@Schema(description = "개발용 매칭 상태 동작 실행 요청")
public record ExecuteDevMatchingActionRequest(
        @NotNull(message = "actionKey는 필수입니다.")
        @Schema(description = "상세 조회가 허용한 동작 key", example = "INSTRUCTOR_ACCEPT")
        DevMatchingActionKey actionKey,
        @NotBlank(message = "stateToken은 필수입니다.")
        @Schema(description = "상세 조회에서 받은 상태 fingerprint")
        String stateToken
) {
}
