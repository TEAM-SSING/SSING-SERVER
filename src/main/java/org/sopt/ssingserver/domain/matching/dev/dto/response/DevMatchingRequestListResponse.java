package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "개발용 매칭 요청 목록")
public record DevMatchingRequestListResponse(
        @Schema(description = "DB 조회 완료 시각", example = "2026-07-15T00:01:00Z")
        Instant observedAt,
        @Schema(description = "현재 페이지 번호", example = "0")
        int page,
        @Schema(description = "페이지 크기", example = "50")
        int size,
        @Schema(description = "전체 매칭 요청 수", example = "3")
        long totalElements,
        @Schema(description = "전체 페이지 수", example = "1")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "false")
        boolean hasNext,
        @Schema(description = "현재 페이지의 매칭 요청 요약")
        List<DevMatchingRequestSummaryResponse> requests
) {
}
