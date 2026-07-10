package org.sopt.ssingserver.global.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SwaggerApiDocsOwnershipTest {

    private static final Path DOMAIN_SOURCE_ROOT = Path.of("src/main/java/org/sopt/ssingserver/domain");
    private static final Pattern API_DOCS_IMPLEMENTATION = Pattern.compile(
            "\\bimplements\\s+[^{\\n]*ApiDocs\\b"
    );

    @Test
    void REST_Controller는_ApiDocs를_구현하고_HTML_Controller는_Hidden이어야_한다() throws IOException {
        List<Path> controllerFiles = findControllerFiles();

        assertThat(controllerFiles)
                .as("검증 대상 REST Controller가 없으면 Swagger ownership 검증이 무의미합니다.")
                .isNotEmpty();

        List<String> violations = controllerFiles.stream()
                .flatMap(SwaggerApiDocsOwnershipTest::ownershipViolations)
                .toList();

        assertThat(violations)
                .as("""
                        REST JSON Controller는 controller.docs의 *ApiDocs를 구현해야 합니다.
                        HTML 개발 도구는 REST Swagger 문서에서 @Hidden으로 제외해야 합니다.
                        위반 목록:
                        %s
                        """, String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private static List<Path> findControllerFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(DOMAIN_SOURCE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/controller/docs/"))
                    .toList();
        }
    }

    private static Stream<String> ownershipViolations(Path path) {
        try {
            String source = Files.readString(path);
            if (!source.contains("@RestController")) {
                return Stream.empty();
            }
            boolean htmlController = source.contains("MediaType.TEXT_HTML_VALUE");
            if (htmlController) {
                return source.contains("@Hidden")
                        ? Stream.empty()
                        : Stream.of(path + " - HTML Controller에 @Hidden 누락");
            }
            return API_DOCS_IMPLEMENTATION.matcher(source).find()
                    ? Stream.empty()
                    : Stream.of(path + " - *ApiDocs 구현 누락");
        } catch (IOException exception) {
            throw new IllegalStateException("Controller 소스 파일을 읽지 못했습니다: " + path, exception);
        }
    }
}
