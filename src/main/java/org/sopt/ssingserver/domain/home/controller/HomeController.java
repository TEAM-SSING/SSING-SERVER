package org.sopt.ssingserver.domain.home.controller;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.home.controller.docs.HomeApiDocs;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.domain.home.service.HomeService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumer/home")
public class HomeController implements HomeApiDocs {

    private final HomeService homeService;

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @GetMapping
    public ResponseEntity<BaseResponse<ConsumerHomeResponse>> getHome(CurrentMember currentMember) {
        ConsumerHomeResponse response = homeService.getHome(currentMember.memberId());
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, response);
    }
}
