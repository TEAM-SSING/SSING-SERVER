package org.sopt.ssingserver.domain.instructor.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "개발용 실제 카카오 회원 강사 관리 목록")
public record DevInstructorMemberListResponse(
        @Schema(description = "DB 조회 완료 시각")
        Instant observedAt,
        @Schema(description = "현재 페이지 번호")
        int page,
        @Schema(description = "페이지 크기")
        int size,
        @Schema(description = "전체 실제 카카오 회원 수")
        long totalElements,
        @Schema(description = "전체 페이지 수")
        int totalPages,
        @Schema(description = "이전 페이지 존재 여부")
        boolean hasPrevious,
        @Schema(description = "다음 페이지 존재 여부")
        boolean hasNext,
        @Schema(description = "DB에서 읽은 리조트 선택지")
        List<DevInstructorResortOptionResponse> resorts,
        @Schema(description = "신청 만들기 시 자동 입력되는 테스트 값")
        DevInstructorApplicationDefaultsResponse applicationDefaults,
        @Schema(description = "현재 페이지의 실제 카카오 회원")
        List<DevInstructorMemberResponse> members
) {
}
