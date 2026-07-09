package org.sopt.ssingserver.domain.home.controller;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.home.controller.docs.HomeApiDocs;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.domain.home.dto.response.InstructorHomeResponse;
import org.sopt.ssingserver.domain.home.service.ConsumerHomeService;
import org.sopt.ssingserver.domain.home.service.InstructorHomeService;
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
@RequestMapping("/api/v1")
public class HomeController implements HomeApiDocs {

    private final ConsumerHomeService consumerHomeService;
    private final InstructorHomeService instructorHomeService;

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @GetMapping("/consumer/home")
    public ResponseEntity<BaseResponse<ConsumerHomeResponse>> getConsumerHome(CurrentMember currentMember) {
        ConsumerHomeResponse response = consumerHomeService.getConsumerHome(currentMember.memberId());
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, response);
    }

    @Override
    @RequireAccess(AccessPolicy.APPROVED_INSTRUCTOR)
    @GetMapping("/instructor/home")
    public ResponseEntity<BaseResponse<InstructorHomeResponse>> getInstructorHome(CurrentMember currentMember) {
        InstructorHomeResponse response = instructorHomeService.getInstructorHome(currentMember.memberId());
        return SuccessResponseFactory.success(CommonSuccessCode.SUCCESS, response);
    }
}
