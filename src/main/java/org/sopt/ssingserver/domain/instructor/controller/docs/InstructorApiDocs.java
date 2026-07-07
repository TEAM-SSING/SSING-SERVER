package org.sopt.ssingserver.domain.instructor.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Instructor", description = "강사 기능 API")
public interface InstructorApiDocs {

    @Operation(
            summary = "강사 즉시 매칭 조건 저장 및 시작",
            description = "강사가 즉시 매칭에 노출될 조건을 저장하고 매칭을 시작합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "즉시 매칭 조건 저장 및 매칭 시작 성공")
    ResponseEntity<BaseResponse<InstructorMatchingExposureResponse>> startExposure(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Valid @RequestBody InstructorMatchingExposureRequest request
    );
}
