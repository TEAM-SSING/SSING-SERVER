package org.sopt.ssingserver.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ControllerAuthenticationPrincipalGuardTest {

    private static final Path DOMAIN_SOURCE_ROOT = Path.of("src/main/java/org/sopt/ssingserver/domain");
    private static final Pattern AUTHENTICATED_MEMBER_USAGE = Pattern.compile(
            "\\bAuthenticatedMember\\s+\\w+|org\\.sopt\\.ssingserver\\.global\\.security\\.AuthenticatedMember"
    );

    @Test
    void domain_Controller는_AuthenticationPrincipal과_AuthenticatedMember를_직접_사용하지_않는다() throws IOException {
        List<Path> controllerFiles = findControllerFiles();

        assertThat(controllerFiles)
                .as("검증 대상 domain Controller 파일이 없으면 정책 검증이 무의미합니다.")
                .isNotEmpty();

        List<String> violations = controllerFiles.stream()
                .flatMap(ControllerAuthenticationPrincipalGuardTest::forbiddenUsages)
                .toList();

        assertThat(violations)
                .as("""
                        보호 API Controller는 @AuthenticationPrincipal AuthenticatedMember를 직접 받지 않습니다.
                        @RequireAccess로 접근 조건을 선언하고, Controller 파라미터에서는 CurrentMember를 사용하세요.
                        AuthenticatedMember는 security/access 내부 전달 객체로만 사용합니다.
                        위반 목록:
                        %s
                        """, String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private static List<Path> findControllerFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(DOMAIN_SOURCE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ControllerAuthenticationPrincipalGuardTest::isRuntimeController)
                    .toList();
        }
    }

    private static boolean isRuntimeController(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        return normalizedPath.contains("/controller/")
                && !normalizedPath.contains("/controller/docs/");
    }

    private static Stream<String> forbiddenUsages(Path path) {
        try {
            String source = Files.readString(path);
            return forbiddenUsageReasons(source).stream()
                    .map(reason -> path + " - " + reason);
        } catch (IOException exception) {
            throw new IllegalStateException("Controller 소스 파일을 읽지 못했습니다: " + path, exception);
        }
    }

    private static List<String> forbiddenUsageReasons(String source) {
        return Stream.of(
                        source.contains("@AuthenticationPrincipal")
                                ? "@AuthenticationPrincipal 직접 사용"
                                : null,
                        AUTHENTICATED_MEMBER_USAGE.matcher(source).find()
                                ? "AuthenticatedMember 직접 사용"
                                : null
                )
                .filter(reason -> reason != null)
                .toList();
    }
}
