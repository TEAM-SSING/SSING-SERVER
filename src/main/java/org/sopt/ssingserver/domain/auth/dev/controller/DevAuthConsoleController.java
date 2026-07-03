package org.sopt.ssingserver.domain.auth.dev.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
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
