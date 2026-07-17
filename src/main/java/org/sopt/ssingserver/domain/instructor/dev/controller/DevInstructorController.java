package org.sopt.ssingserver.domain.instructor.dev.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.dev.controller.docs.DevInstructorApiDocs;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.ExecuteDevInstructorActionRequest;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorActionExecutionResponse;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorMemberListResponse;
import org.sopt.ssingserver.domain.instructor.dev.service.DevInstructorActionService;
import org.sopt.ssingserver.domain.instructor.dev.service.DevInstructorQueryService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 실제 dev 회원 상태를 바꾸므로 별도 접근 경계가 정해질 때까지 Swagger Try it out에서 숨긴다.
@Hidden
@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-instructor-actions.enabled", havingValue = "true")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev/auth/kakao-members")
public class DevInstructorController implements DevInstructorApiDocs {

    private final DevInstructorQueryService queryService;
    private final DevInstructorActionService actionService;

    @Override
    @GetMapping
    public ResponseEntity<BaseResponse<DevInstructorMemberListResponse>> getMembers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size
    ) {
        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                queryService.getMembers(page, size)
        );
    }

    @Override
    @PostMapping("/{memberId}/instructor-actions")
    public ResponseEntity<BaseResponse<DevInstructorActionExecutionResponse>> executeAction(
            @PathVariable Long memberId,
            @Valid @RequestBody ExecuteDevInstructorActionRequest request
    ) {
        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                actionService.execute(memberId, request)
        );
    }
}
