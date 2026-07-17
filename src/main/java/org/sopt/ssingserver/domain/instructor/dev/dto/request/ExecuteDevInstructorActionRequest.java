package org.sopt.ssingserver.domain.instructor.dev.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;

@Schema(description = "개발용 실제 카카오 회원 강사 동작 요청")
public record ExecuteDevInstructorActionRequest(
        @NotNull(message = "actionKey는 필수입니다.")
        @Schema(description = "목록 조회가 현재 허용한 동작 key", example = "APPROVE_WITH_CONFIGURATION")
        DevInstructorActionKey actionKey,

        @NotBlank(message = "stateToken은 필수입니다.")
        @Schema(description = "목록 조회에서 받은 최신 상태 fingerprint")
        String stateToken,

        @Valid
        @Schema(description = "승인 또는 설정 저장 동작에서만 필수인 선택값")
        DevInstructorConfigurationRequest configuration
) {
}
