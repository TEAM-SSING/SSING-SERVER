package org.sopt.ssingserver.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.sopt.ssingserver.domain.notification.enums.ClientPlatform;

public record RegisterFcmTokenRequest(
        @Schema(description = "요청을 보낸 앱 유형", example = "CONSUMER")
        @NotNull(message = "앱 유형은 필수입니다.")
        ClientApp clientApp,

        @Schema(description = "클라이언트 플랫폼", example = "ANDROID")
        @NotNull(message = "플랫폼은 필수입니다.")
        ClientPlatform platform,

        @Schema(description = "FCM registration token", example = "fcm_token_xxx")
        @NotBlank(message = "FCM token은 필수입니다.")
        @Size(max = 512, message = "FCM token은 512자를 초과할 수 없습니다.")
        String fcmToken
) {
}
