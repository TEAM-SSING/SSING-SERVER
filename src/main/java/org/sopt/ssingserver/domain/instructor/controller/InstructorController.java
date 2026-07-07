package org.sopt.ssingserver.domain.instructor.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.controller.docs.InstructorApiDocs;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;
import org.sopt.ssingserver.domain.instructor.response.InstructorSuccessCode;
import org.sopt.ssingserver.domain.instructor.service.InstructorService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/instructor/matching-exposure")
public class InstructorController implements InstructorApiDocs {

    private final InstructorService instructorService;

    @Override
    @RequireAccess(AccessPolicy.APPROVED_INSTRUCTOR)
    @PutMapping
    public ResponseEntity<BaseResponse<InstructorMatchingExposureResponse>> startExposure(
            CurrentMember currentMember,
            @Valid @RequestBody InstructorMatchingExposureRequest request
    ) {
        boolean isExposed = instructorService.startExposure(currentMember.memberId(), request);
        InstructorMatchingExposureResponse response = InstructorMatchingExposureResponse.from(isExposed);
        return SuccessResponseFactory.success(InstructorSuccessCode.INSTRUCTOR_MATCHING_EXPOSURE_STARTED, response);
    }
}
