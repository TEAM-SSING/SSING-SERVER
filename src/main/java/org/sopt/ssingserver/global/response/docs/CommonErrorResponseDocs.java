package org.sopt.ssingserver.global.response.docs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CommonErrorResponse", description = "공통 실패 응답")
public record CommonErrorResponseDocs(
        @Schema(description = "요청 실패 여부", example = "false")
        boolean success,

        @Schema(description = "실패 원인을 나타내는 응답 코드", example = "ERROR_CODE")
        String code,

        @Schema(description = "실패 사유 메시지", example = "요청 처리에 실패했습니다.")
        String message,

        @Schema(description = "서버 로그 추적용 요청 ID", example = "req_abc123")
        String requestId
) {
}
