package org.sopt.ssingserver.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record DeleteFcmTokenRequest(
        @Schema(description = "삭제할 FCM registration token", example = "fcm_token_xxx")
        @NotBlank(message = "FCM token은 필수입니다.")
        String fcmToken
) {
}
