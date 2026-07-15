package org.sopt.ssingserver.domain.notification.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Notification", description = "알림 API")
public interface NotificationApiDocs {

    @Operation(
            summary = "알림 목록 조회",
            description = "현재 로그인한 소비자 또는 승인된 강사의 최근 7일 이내 알림 목록을 최신순으로 커서 기반 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "알림 목록 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {
                    "VALIDATION_FAILED",
                    "BAD_REQUEST",
                    "UNAUTHENTICATED",
                    "FORBIDDEN",
                    "INTERNAL_ERROR"
            }
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    ResponseEntity<BaseResponse<NotificationListResponse>> getNotifications(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "다음 페이지 조회용 커서 (createdAt_notificationId 형식)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "조회할 알림 개수")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );
}
