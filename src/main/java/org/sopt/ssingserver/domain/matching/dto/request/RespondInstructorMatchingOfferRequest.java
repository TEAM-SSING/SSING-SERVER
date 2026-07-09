package org.sopt.ssingserver.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferDecision;

@Schema(description = "강사 매칭 제안 응답 요청")
public record RespondInstructorMatchingOfferRequest(

        @NotNull
        @Schema(description = "강사 응답", example = "ACCEPTED", allowableValues = {"ACCEPTED", "REJECTED"})
        MatchingOfferDecision decision
) {
}
