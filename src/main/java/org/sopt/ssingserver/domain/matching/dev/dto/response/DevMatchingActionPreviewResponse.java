package org.sopt.ssingserver.domain.matching.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;

@Schema(description = "현재 snapshot에서 실행 가능한 동작의 영향 미리보기")
public record DevMatchingActionPreviewResponse(
        @Schema(description = "후속 실행 API에서 사용할 동작 key", example = "INSTRUCTOR_ACCEPT")
        DevMatchingActionKey actionKey,
        @Schema(description = "화면 표시용 동작 이름", example = "강사 수락")
        String label,
        @Schema(description = "동작을 수행하는 사람")
        DevMatchingPersonResponse actor,
        @Schema(description = "동작 결과에 영향을 받는 사람")
        List<DevMatchingPersonResponse> affectedPeople,
        @Schema(description = "동작 결과에 영향을 받는 원본 리소스")
        List<AffectedResource> affectedResources,
        @Schema(description = "조건에 따라 가능한 예상 결과")
        List<Outcome> outcomes,
        @Schema(description = "실행하지 않는 미리보기임을 나타냄", example = "true")
        boolean previewOnly
) {

    @Schema(name = "DevMatchingAffectedResource", description = "영향을 받는 원본 리소스 식별자")
    public record AffectedResource(
            @Schema(description = "리소스 종류", example = "MATCHING_OFFER")
            DevMatchingResourceType resourceType,
            @Schema(description = "리소스 ID. 새로 생성될 row이면 null", example = "77")
            Long resourceId
    ) {
    }

    @Schema(name = "DevMatchingActionOutcome", description = "동작의 한 가지 예상 결과")
    public record Outcome(
            @Schema(description = "예상 결과 key", example = "PAYMENT_PENDING")
            String outcomeKey,
            @Schema(description = "이 결과가 발생하는 조건")
            String condition,
            @Schema(description = "실행 시점 조건에 따라 달라지는 결과인지 여부", example = "false")
            boolean conditional,
            @Schema(description = "사람별 앱 매칭 상태 변화")
            List<PersonStatusChange> personStatusChanges,
            @Schema(description = "원본 리소스별 필드 변화")
            List<ResourceStateChange> resourceStateChanges,
            @Schema(description = "결과 해석 또는 제한 사항")
            String note
    ) {
    }

    @Schema(name = "DevMatchingPersonStatusChange", description = "한 사람의 계산 상태 변화")
    public record PersonStatusChange(
            @Schema(description = "상태가 바뀌는 사람")
            DevMatchingPersonResponse person,
            @Schema(description = "변경 전 앱 매칭 상태", example = "WAITING_FOR_INSTRUCTOR")
            MatchingStatus before,
            @Schema(description = "변경 후 앱 매칭 상태", example = "WAITING_FOR_CONFIRMATION")
            MatchingStatus after
    ) {
    }

    @Schema(name = "DevMatchingResourceStateChange", description = "한 원본 리소스 필드의 변화")
    public record ResourceStateChange(
            @Schema(description = "상태가 바뀌는 리소스")
            AffectedResource resource,
            @Schema(description = "바뀌는 필드 이름", example = "status")
            String field,
            @Schema(description = "변경 전 값", example = "OFFERED")
            String before,
            @Schema(description = "변경 후 값", example = "ACCEPTED")
            String after
    ) {
    }
}
