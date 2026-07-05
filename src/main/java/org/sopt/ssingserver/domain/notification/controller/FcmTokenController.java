package org.sopt.ssingserver.domain.notification.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.notification.controller.docs.FcmTokenApiDocs;
import org.sopt.ssingserver.domain.notification.dto.request.DeleteFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.dto.request.RegisterFcmTokenRequest;
import org.sopt.ssingserver.domain.notification.response.FcmTokenSuccessCode;
import org.sopt.ssingserver.domain.notification.service.FcmTokenService;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fcm-tokens")
public class FcmTokenController implements FcmTokenApiDocs {

    private final FcmTokenService fcmTokenService;

    @Override
    @PutMapping
    public ResponseEntity<Void> registerOrUpdate(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody RegisterFcmTokenRequest request
    ) {
        fcmTokenService.registerOrUpdate(authenticatedMember.memberId(), request);
        return SuccessResponseFactory.noContent(FcmTokenSuccessCode.FCM_TOKEN_REGISTERED_OR_UPDATED);
    }

    @Override
    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody DeleteFcmTokenRequest request
    ) {
        fcmTokenService.unregister(authenticatedMember.memberId(), request);
        return SuccessResponseFactory.noContent(FcmTokenSuccessCode.FCM_TOKEN_DELETED);
    }
}
