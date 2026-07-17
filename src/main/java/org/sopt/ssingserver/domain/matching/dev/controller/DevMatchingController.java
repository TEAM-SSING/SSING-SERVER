package org.sopt.ssingserver.domain.matching.dev.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dev.controller.docs.DevMatchingApiDocs;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestDetailResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestListResponse;
import org.sopt.ssingserver.domain.matching.dev.service.DevMatchingQueryService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev/matching")
public class DevMatchingController implements DevMatchingApiDocs {

    private final DevMatchingQueryService devMatchingQueryService;

    @Override
    @GetMapping("/requests")
    public ResponseEntity<BaseResponse<DevMatchingRequestListResponse>> getRequests(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                devMatchingQueryService.getRequests(page, size)
        );
    }

    @Override
    @GetMapping("/requests/{matchingRequestId}")
    public ResponseEntity<BaseResponse<DevMatchingRequestDetailResponse>> getRequest(
            @PathVariable Long matchingRequestId
    ) {
        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                devMatchingQueryService.getRequest(matchingRequestId)
        );
    }
}
