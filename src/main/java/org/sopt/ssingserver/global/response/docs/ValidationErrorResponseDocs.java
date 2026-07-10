package org.sopt.ssingserver.global.response.docs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(name = "ValidationErrorResponse", description = "필드별 검증 실패 응답")
public record ValidationErrorResponseDocs(
        @Schema(
                description = "요청 실패 여부",
                example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean success,

        @Schema(
                description = "검증 실패 응답 코드",
                example = "VALIDATION_FAILED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String code,

        @Schema(
                description = "검증 실패 메시지",
                example = "요청 값 검증에 실패했습니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String message,

        @Schema(
                description = "필드명을 key, 사용자 표시 메시지를 value로 갖는 오류 목록",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Map<String, String> errors,

        @Schema(
                description = "서버 로그 추적용 요청 ID",
                example = "req_abc123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String requestId
) {
}
