package org.sopt.ssingserver.domain.matching.dev.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DevMatchingConsoleResourceTest {

    @Test
    void HTML_매칭_콘솔만_Swagger에서_숨긴다() {
        assertThat(DevMatchingConsoleController.class.isAnnotationPresent(Hidden.class)).isTrue();
        assertThat(DevMatchingController.class.getInterfaces())
                .extracting(Class::getSimpleName)
                .containsExactly("DevMatchingApiDocs");
    }

    @Test
    void 매칭_콘솔은_UTF8_HTML로_응답한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DevMatchingConsoleController())
                .build();

        mockMvc.perform(get("/dev/matching/console"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(new MediaType("text", "html", StandardCharsets.UTF_8)));
    }

    @Test
    void 매칭_콘솔은_목록_상세_새로고침과_영향미리보기만_제공한다() throws IOException {
        String html = resource("dev-matching-console.html");

        assertThat(html)
                .contains("왼쪽에서 매칭 요청을 선택하세요")
                .contains("/dev/matching/requests?page=")
                .contains("/dev/matching/requests/${matchingRequestId}")
                .contains("id=\"refresh\"")
                .contains("id=\"autoRefresh\"")
                .contains("id=\"actionDialog\"")
                .contains("현재 페이지에서 persona")
                .contains("elements.refresh.addEventListener(\"click\", () => loadList(true))")
                .contains("setInterval(() => loadList(true), 10_000)")
                .contains("await loadDetail(state.selectedId, true)")
                .contains("detailSequence")
                .contains("payload.totalPages > 0 && state.page >= payload.totalPages")
                .contains("__SSING_DEV_MATCHING_TEST_HOOK__")
                .contains("button.addEventListener(\"click\", () => openActionPreview")
                .contains("affectedPeople")
                .contains("affectedResources")
                .contains("requestRelations")
                .contains("요청별 연결표")
                .contains("<th>Name</th>")
                .contains("person.name || \"-\"")
                .contains("preview only")
                .contains("결제 대기")
                .contains("PAYMENT_PENDING")
                .contains("stateToken")
                .contains("/dev/auth/console")
                .contains("/adminer/")
                .contains("공유 Dev DB는 항상 <code>idle-base</code>로 reset됩니다")
                .doesNotContain("<code>matching-price-vivaldi</code>로 dev DB를 reset")
                .doesNotContain("method: \"POST\"")
                .doesNotContain("method: \"PATCH\"")
                .doesNotContain("method: \"DELETE\"");
    }

    @Test
    void 두_콘솔은_같은_개발도구_셸에서_현재_페이지와_이동대상을_명확히_보여준다() throws IOException {
        String personaHtml = resource("dev-auth-console.html");
        String matchingHtml = resource("dev-matching-console.html");
        String personaNav = navigation(personaHtml);
        String matchingNav = navigation(matchingHtml);

        assertCommonShell(personaHtml, personaNav);
        assertCommonShell(matchingHtml, matchingNav);

        assertThat(personaNav)
                .contains("class=\"active\" href=\"/dev/auth/console\" aria-current=\"page\">Persona</a>")
                .contains("<a href=\"/dev/matching/console\">Matching</a>");
        assertThat(matchingNav)
                .contains("<a href=\"/dev/auth/console\">Persona</a>")
                .contains("class=\"active\" href=\"/dev/matching/console\" aria-current=\"page\">Matching</a>");
        assertThat(personaNav.split("aria-current=\"page\"", -1)).hasSize(2);
        assertThat(matchingNav.split("aria-current=\"page\"", -1)).hasSize(2);
    }

    private static void assertCommonShell(String html, String navigation) {
        assertThat(html)
                .contains("<header class=\"dev-console-header\" data-dev-console-shell=\"v1\">")
                .contains("class=\"dev-console-brand\"")
                .contains("<main id=\"main-content\"")
                .contains(".dev-console-brand:focus-visible")
                .contains(".dev-nav a:focus-visible")
                .contains(".dev-nav { width: 100%; margin-left: 0; order: 3; }");

        assertThat(navigation)
                .contains("aria-label=\"개발 도구\"")
                .containsSubsequence(">Persona</a>", ">Matching</a>", ">Adminer ")
                .contains("href=\"/adminer/\" target=\"_blank\" rel=\"noopener noreferrer\" aria-label=\"Adminer 새 탭에서 열기\"")
                .doesNotContain("href=\"/dev/auth/console\" target=")
                .doesNotContain("href=\"/dev/matching/console\" target=");
    }

    private static String navigation(String html) {
        int start = html.indexOf("<nav class=\"dev-nav\"");
        int end = html.indexOf("</nav>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return html.substring(start, end + "</nav>".length());
    }

    private static String resource(String path) throws IOException {
        return new String(
                new ClassPathResource(path).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }
}
