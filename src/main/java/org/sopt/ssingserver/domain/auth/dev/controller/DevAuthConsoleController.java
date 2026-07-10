package org.sopt.ssingserver.domain.auth.dev.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: production 배포 전 dev profile 오적용 대비용 2차 접근 제한(shared secret 또는 IP 제한) 추가
@Profile({"local", "dev"})
@Hidden
@RestController
@RequestMapping("/dev/auth")
public class DevAuthConsoleController {

    @GetMapping(value = "/console", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<ClassPathResource> getConsole() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("dev-auth-console.html"));
    }
}
