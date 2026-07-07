package org.sopt.ssingserver.domain.notification.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.notification.dto.request.DeleteFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.dto.request.RegisterFcmTokenRequest;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "FCM Token", description = "FCM 토큰 관리 API")
public interface FcmTokenApiDocs {

    @Operation(
            summary = "FCM 토큰 등록 및 수정",
            description = "현재 회원의 FCM registration token을 등록하거나 기존 등록 정보를 수정합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "204", description = "FCM 토큰 등록 또는 수정 성공")
    ResponseEntity<Void> registerOrUpdate(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Valid @RequestBody RegisterFcmTokenRequest request
    );

    @Operation(
            summary = "FCM 토큰 삭제",
            description = "현재 회원에게 등록된 FCM registration token을 삭제합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "204", description = "FCM 토큰 삭제 성공")
    ResponseEntity<Void> unregister(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Valid @RequestBody DeleteFcmTokenRequest request
    );
}
