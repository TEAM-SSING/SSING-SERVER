package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType;

@Schema(description = "매칭 상태 판단에 사용한 원본 리소스 row")
public record DevMatchingResourceResponse(
        @Schema(description = "리소스 종류", example = "MATCHING_REQUEST")
        DevMatchingResourceType resourceType,
        @Schema(description = "리소스 ID", example = "301")
        Long resourceId,
        @Schema(description = "원본 상태", example = "MATCHED")
        String status,
        @Schema(description = "원본 상태 사유", example = "INSTRUCTOR_REJECTED")
        String statusReason,
        @Schema(description = "row 생성 시각", example = "2026-07-15T00:00:00Z")
        Instant createdAt,
        @Schema(description = "row 최종 수정 시각", example = "2026-07-15T00:01:00Z")
        Instant updatedAt
) {
}
