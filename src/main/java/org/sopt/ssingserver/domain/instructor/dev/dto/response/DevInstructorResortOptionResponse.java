package org.sopt.ssingserver.domain.instructor.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개발 콘솔에서 선택할 수 있는 DB 리조트")
public record DevInstructorResortOptionResponse(
        @Schema(description = "리조트 내부 ID", example = "1")
        Long resortId,
        @Schema(description = "리조트 코드", example = "VIVALDI")
        String code,
        @Schema(description = "화면 표시 이름", example = "비발디파크")
        String displayName,
        @Schema(description = "리프트권 기준 금액", example = "10000")
        int passFeeAmount
) {
}
