package org.sopt.ssingserver.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.sopt.ssingserver.domain.matching.enums.MatchingConfirmationDecision;

public record RespondConsumerMatchingConfirmationRequest(
        @NotNull(message = "최종 응답은 필수입니다.")
        @Schema(description = "대표 소비자 최종 응답", example = "ACCEPTED")
        MatchingConfirmationDecision decision,

        @Schema(description = "거절 사유", example = "FIND_ANOTHER_INSTRUCTOR")
        String rejectionReason
) {
}
