package org.sopt.ssingserver.domain.instructor.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.controller.docs.InstructorApiDocs;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.dto.result.InstructorMatchingExposureConditionsResult;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureCancelResponse;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureConditionsResponse;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;
import org.sopt.ssingserver.domain.instructor.response.InstructorSuccessCode;
import org.sopt.ssingserver.domain.instructor.service.InstructorService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequireAccess(AccessPolicy.APPROVED_INSTRUCTOR)
@RequestMapping("/api/v1/instructor/matching-exposure")
public class InstructorController implements InstructorApiDocs {

    private final InstructorService instructorService;

    @Override
    @GetMapping
    public ResponseEntity<BaseResponse<InstructorMatchingExposureConditionsResponse>> getExposureConditions(
            CurrentMember currentMember
    ) {
        InstructorMatchingExposureConditionsResult result = instructorService.getExposureConditions(
                currentMember.memberId()
        );
        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                InstructorMatchingExposureConditionsResponse.from(result)
        );
    }

    @Override
    @PutMapping
    public ResponseEntity<BaseResponse<InstructorMatchingExposureResponse>> startExposure(
            CurrentMember currentMember,
            @Valid @RequestBody InstructorMatchingExposureRequest request
    ) {
        InstructorMatchingExposureResponse response = instructorService.startExposure(currentMember.memberId(), request);
        return SuccessResponseFactory.success(InstructorSuccessCode.INSTRUCTOR_MATCHING_EXPOSURE_STARTED, response);
    }

    @Override
    @PostMapping("/cancellation")
    public ResponseEntity<BaseResponse<InstructorMatchingExposureCancelResponse>> cancelExposure(
            CurrentMember currentMember
    ) {
        InstructorMatchingExposureCancelResponse response = instructorService.cancelExposure(currentMember.memberId());
        return SuccessResponseFactory.success(InstructorSuccessCode.INSTRUCTOR_MATCHING_EXPOSURE_CANCELLED, response);
    }
}
