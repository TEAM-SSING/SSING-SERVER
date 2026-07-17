package org.sopt.ssingserver.domain.matching.dev.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dev.controller.docs.DevMatchingActionApiDocs;
import org.sopt.ssingserver.domain.matching.dev.dto.request.ExecuteDevMatchingActionRequest;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionExecutionResponse;
import org.sopt.ssingserver.domain.matching.dev.service.DevMatchingActionService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 조회 API와 분리해 기능 플래그가 꺼져도 dev 매칭 현황은 계속 확인할 수 있게 한다.
@Hidden
@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-matching-actions.enabled", havingValue = "true")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev/matching/requests")
public class DevMatchingActionController implements DevMatchingActionApiDocs {

    private final DevMatchingActionService devMatchingActionService;

    @Override
    @PostMapping("/{matchingRequestId}/actions")
    public ResponseEntity<BaseResponse<DevMatchingActionExecutionResponse>> executeAction(
            @PathVariable Long matchingRequestId,
            @Valid @RequestBody ExecuteDevMatchingActionRequest request
    ) {
        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                devMatchingActionService.execute(matchingRequestId, request)
        );
    }
}
