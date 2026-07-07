package org.sopt.ssingserver.domain.notification.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.notification.controller.docs.FcmTokenApiDocs;
import org.sopt.ssingserver.domain.notification.dto.request.DeleteFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.dto.request.RegisterFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.response.FcmTokenSuccessCode;
import org.sopt.ssingserver.domain.notification.service.FcmTokenService;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireAccess
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fcm-tokens")
public class FcmTokenController implements FcmTokenApiDocs {

    private final FcmTokenService fcmTokenService;

    @Override
    @PutMapping
    public ResponseEntity<Void> registerOrUpdate(
            CurrentMember currentMember,
            @Valid @RequestBody RegisterFcmTokenRequest request
    ) {
        fcmTokenService.registerOrUpdate(currentMember.memberId(), request);
        return SuccessResponseFactory.noContent(FcmTokenSuccessCode.FCM_TOKEN_REGISTERED_OR_UPDATED);
    }

    @Override
    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(
            CurrentMember currentMember,
            @Valid @RequestBody DeleteFcmTokenRequest request
    ) {
        fcmTokenService.unregister(currentMember.memberId(), request);
        return SuccessResponseFactory.noContent(FcmTokenSuccessCode.FCM_TOKEN_DELETED);
    }
}
