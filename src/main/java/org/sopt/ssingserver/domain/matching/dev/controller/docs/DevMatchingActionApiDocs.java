package org.sopt.ssingserver.domain.matching.dev.controller.docs;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.matching.dev.dto.request.ExecuteDevMatchingActionRequest;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionExecutionResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

public interface DevMatchingActionApiDocs {

    // 실제 dev DB를 바꾸므로 공개 Swagger 계약과 Try it out에서는 노출하지 않는다.
    @Hidden
    ResponseEntity<BaseResponse<DevMatchingActionExecutionResponse>> executeAction(
            @PathVariable Long matchingRequestId,
            @Valid @RequestBody ExecuteDevMatchingActionRequest request
    );
}
