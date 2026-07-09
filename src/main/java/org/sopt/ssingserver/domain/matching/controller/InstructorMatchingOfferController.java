package org.sopt.ssingserver.domain.matching.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.controller.docs.InstructorMatchingOfferApiDocs;
import org.sopt.ssingserver.domain.matching.dto.request.RespondInstructorMatchingOfferRequest;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDecisionResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOffersResponse;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOfferDecisionResult;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.response.MatchingSuccessCode;
import org.sopt.ssingserver.domain.matching.service.InstructorMatchingOfferService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequireAccess(AccessPolicy.APPROVED_INSTRUCTOR)
@RequestMapping("/api/v1/instructor/matching-offers")
public class InstructorMatchingOfferController implements InstructorMatchingOfferApiDocs {

    private final InstructorMatchingOfferService instructorMatchingOfferService;

    @Override
    @GetMapping
    public ResponseEntity<BaseResponse<InstructorMatchingOffersResponse>> getCurrentOffers(
            CurrentMember currentMember,
            @RequestParam(defaultValue = "OFFERED") MatchingOfferStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        InstructorMatchingOffersResult result = instructorMatchingOfferService.getCurrentOffers(
                currentMember.memberId(),
                status,
                page,
                size
        );

        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, InstructorMatchingOffersResponse.from(result));
    }

    @Override
    @PatchMapping("/{offerId}")
    public ResponseEntity<BaseResponse<InstructorMatchingOfferDecisionResponse>> respond(
            CurrentMember currentMember,
            @PathVariable Long offerId,
            @Valid @RequestBody RespondInstructorMatchingOfferRequest request
    ) {
        InstructorMatchingOfferDecisionResult result = instructorMatchingOfferService.respond(
                currentMember.memberId(),
                offerId,
                request.decision()
        );

        return SuccessResponseFactory.success(
                MatchingSuccessCode.MATCHING_OFFER_RESPONDED,
                InstructorMatchingOfferDecisionResponse.from(result)
        );
    }
}
