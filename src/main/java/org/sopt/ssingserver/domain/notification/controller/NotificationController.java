package org.sopt.ssingserver.domain.notification.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.notification.controller.docs.NotificationApiDocs;
import org.sopt.ssingserver.domain.notification.dto.response.NotificationListResponse;
import org.sopt.ssingserver.domain.notification.service.NotificationService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireAccess({AccessPolicy.CONSUMER, AccessPolicy.APPROVED_INSTRUCTOR})
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController implements NotificationApiDocs {

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ResponseEntity<BaseResponse<NotificationListResponse>> getNotifications(
            CurrentMember currentMember,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회할 알림 개수는 1개 이상이어야 합니다.")
            @Max(value = 100, message = "조회할 알림 개수는 100개 이하여야 합니다.")
            int size
    ) {
        NotificationListResponse response = notificationService.getNotifications(currentMember, cursor, size);
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, response);
    }
}
