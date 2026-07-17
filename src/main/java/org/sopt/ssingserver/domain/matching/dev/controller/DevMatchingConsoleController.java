package org.sopt.ssingserver.domain.matching.dev.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@Hidden
@RestController
@RequestMapping("/dev/matching")
public class DevMatchingConsoleController {

    private static final MediaType TEXT_HTML_UTF8 =
            new MediaType("text", "html", StandardCharsets.UTF_8);

    @GetMapping(value = "/console", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<ClassPathResource> getConsole() {
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(new ClassPathResource("dev-matching-console.html"));
    }
}
