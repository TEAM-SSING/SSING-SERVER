package org.sopt.ssingserver.domain.auth.dev.controller;

import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dev.dto.request.CreateDevPersonaRequest;
import org.sopt.ssingserver.domain.auth.dev.dto.request.DevAuthTokenRequest;
import org.sopt.ssingserver.domain.auth.dev.dto.response.CreateDevPersonaResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevAuthTokenResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevPersonaListResponse;
import org.sopt.ssingserver.domain.auth.dev.response.DevAuthSuccessCode;
import org.sopt.ssingserver.domain.auth.dev.service.DevAuthService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: production 배포 전 dev profile 오적용 대비용 2차 접근 제한(shared secret 또는 IP 제한) 추가
@Profile({"local", "dev"})
@RestController
@RequestMapping("/dev/auth")
public class DevAuthController {

    private final DevAuthService devAuthService;

    public DevAuthController(DevAuthService devAuthService) {
        this.devAuthService = devAuthService;
    }

    @GetMapping("/personas")
    public ResponseEntity<BaseResponse<DevPersonaListResponse>> getPersonas() {
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, devAuthService.getPersonas());
    }

    @PostMapping("/personas")
    public ResponseEntity<BaseResponse<CreateDevPersonaResponse>> createPersona(
            @Valid @RequestBody CreateDevPersonaRequest request
    ) {
        CreateDevPersonaResponse response = devAuthService.createPersona(
                request.personaKey(),
                request.nickname(),
                request.template()
        );
        return SuccessResponseFactory.success(DevAuthSuccessCode.DEV_PERSONA_CREATED, response);
    }

    @PostMapping("/token")
    public ResponseEntity<BaseResponse<DevAuthTokenResponse>> issueToken(
            @Valid @RequestBody DevAuthTokenRequest request
    ) {
        DevAuthTokenResponse response = devAuthService.issueToken(request.personaKey());
        return SuccessResponseFactory.success(DevAuthSuccessCode.DEV_AUTH_TOKEN_ISSUED, response);
    }
}
