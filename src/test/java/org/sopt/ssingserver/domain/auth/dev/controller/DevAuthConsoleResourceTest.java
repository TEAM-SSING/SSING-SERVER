package org.sopt.ssingserver.domain.auth.dev.controller;

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

class DevAuthConsoleResourceTest {

    @Test
    void HTML_콘솔은_REST_Swagger_문서에서_숨긴다() {
        assertThat(DevAuthConsoleController.class.isAnnotationPresent(Hidden.class)).isTrue();
    }

    @Test
    void HTML_콘솔은_UTF8_문자셋을_응답_헤더에_명시한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DevAuthConsoleController())
                .build();

        mockMvc.perform(get("/dev/auth/console"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(new MediaType("text", "html", StandardCharsets.UTF_8)));
    }

    @Test
    void consoleHtml은_다크_콘솔과_타이포그래피_토큰을_제공한다() throws IOException {
        String html = consoleHtml();

        assertThat(html)
                .contains("color-scheme: dark")
                .contains("--font-size-micro: 0.75rem")
                .contains("--font-size-caption: 0.8125rem")
                .contains("--font-size-body: 0.875rem")
                .contains("--font-size-title-md: 1.375rem")
                .contains("--type-page-title-size")
                .contains("snowfield-backdrop")
                .contains("illustration-symbols")
                .contains("Arctic Ski Lodge Console inline SVG system")
                .contains("viewBox=\"0 0 160 120\"")
                .contains("symbol id=\"illustration-student\"")
                .contains("symbol id=\"illustration-instructor\"")
                .contains("symbol id=\"illustration-instructor-pending\"")
                .contains("symbol id=\"illustration-instructor-approved\"")
                .contains("symbol id=\"illustration-student-suspended\"")
                .contains("symbol id=\"illustration-bg-penguin-play\"")
                .contains("symbol id=\"illustration-bg-bear-guide\"")
                .contains("symbol id=\"illustration-bg-fox-peek\"")
                .contains("symbol id=\"illustration-bg-seal-peek\"")
                .contains("backdrop-playmate")
                .contains("backdrop-primary")
                .contains("backdrop-faint")
                .contains("choice-svg")
                .contains("createSvgArtwork")
                .contains("roleArtwork")
                .contains("statusArtwork")
                .contains("status-active-svg")
                .contains("GENERAL_CONSUMER: createSvgArtwork(\"illustration-student\", \"status-active-svg\")")
                .contains("#roleChoices")
                .contains(".role-card .choice-visual")
                .contains(".field[hidden]")
                .contains("min-height: 132px")
                .contains("radial-gradient(circle at 50% 45%")
                .contains("transform: scale(1.22)")
                .contains("status-card")
                .contains(".status-card .choice-visual")
                .contains(".status-card .choice-svg")
                .contains(".modal .token-panel")
                .contains("#modalTokenPanel:empty")
                .contains("#modalStatus:empty")
                .contains("min-height: 40px")
                .contains("ssing-brand")
                .contains("data-node-id=\"273:2055\"")
                .contains("viewBox=\"0 0 167.15 125.969\"")
                .contains("aria-label=\"SSING\"")
                .contains("data-role-filter=\"CONSUMER\"")
                .contains("생성 일시")
                .contains("persona-list-header")
                .contains("column-header")
                .contains("sort-header")
                .contains("sort-arrow")
                .contains("data-sort-key=\"createdAt\"")
                .contains("data-sort-key=\"template\"")
                .contains("data-sort-key=\"role\"")
                .contains("selectSortColumn")
                .contains("aria-sort")
                .contains("--persona-grid-columns")
                .contains("--persona-grid-gap")
                .contains("grid-template-columns: var(--persona-grid-columns)")
                .contains("column-gap: var(--persona-grid-gap)")
                .contains("상태 템플릿")
                .contains("회원 상태")
                .contains("강사 승인")
                .contains("issuedTokensByPersonaKey")
                .contains("data-issue-token")
                .contains(".token-panel[hidden]")
                .contains("data-toggle-token-panel")
                .contains("toggleTokenPanel")
                .contains("토큰 발급")
                .contains("보기")
                .contains("접기")
                .contains("생성 완료")
                .contains("복사 완료")
                .contains("icon-button modal-close")
                .contains("id=\"closeCreateModal\" aria-label=\"닫기\"")
                .contains("toolbar-actions")
                .contains("toolbar-controls")
                .contains("toolbar-meta")
                .contains("button primary toolbar-create")
                .contains("icon-button refresh-button")
                .contains("id=\"refreshPersonas\" type=\"button\" aria-label=\"새로고침\"")
                .doesNotContain("<select id=\"sortOrder\">")
                .doesNotContain("sortOrderSelect")
                .doesNotContain("최신 생성순")
                .doesNotContain("id=\"closeCreateModal\">닫기</button>")
                .doesNotContain("id=\"refreshPersonas\">새로고침</button>")
                .doesNotContain("Publicdomainvectors.org")
                .doesNotContain("SVG Repo CC0")
                .doesNotContain("illustration-snow-mountain")
                .doesNotContain("illustration-cableway")
                .doesNotContain("illustration-ski-pass-base")
                .doesNotContain("illustration-student-active")
                .doesNotContain("roleIllustrations")
                .doesNotContain("templateIllustrations")
                .doesNotContain("role-art")
                .doesNotContain("status-art");
    }

    @Test
    void 유저_생성_입력칸은_예시를_placeholder로_보여주고_설명을_라벨_오른쪽에_둔다() throws IOException {
        String html = consoleHtml();

        assertThat(html)
                .contains("field-help-inline")
                .contains("text-overflow: ellipsis")
                .contains("id=\"personaKeyHelp\"")
                .contains("placeholder=\"예: consumer-matching-A\"")
                .contains("aria-describedby=\"personaKeyHelp\"")
                .contains("id=\"nicknameHelp\"")
                .contains("placeholder=\"예: 매칭 강습생 A\"")
                .contains("aria-describedby=\"nicknameHelp\"");
        assertThat(html.indexOf("id=\"personaKeyHelp\""))
                .isLessThan(html.indexOf("<input id=\"personaKey\""));
        assertThat(html.indexOf("id=\"nicknameHelp\""))
                .isLessThan(html.indexOf("<input id=\"nickname\""));
        assertThat(html)
                .doesNotContain("<p class=\"field-help\" id=\"personaKeyHelp\"")
                .doesNotContain("<p class=\"field-help\" id=\"nicknameHelp\"");
    }

    @Test
    void 콘솔_리스트는_페르소나와_닉네임을_별도_열로_보여주고_생성일시_셀_라벨을_반복하지_않는다() throws IOException {
        String html = consoleHtml();

        assertThat(html)
                .contains("data-sort-cell=\"personaKey\"")
                .contains("<span>페르소나</span>")
                .contains("data-sort-cell=\"nickname\"")
                .contains("<span>닉네임</span>")
                .contains("data-sort-cell=\"template\"");
        assertThat(html.indexOf("data-sort-cell=\"personaKey\""))
                .isLessThan(html.indexOf("data-sort-cell=\"nickname\""));
        assertThat(html.indexOf("data-sort-cell=\"nickname\""))
                .isLessThan(html.indexOf("data-sort-cell=\"template\""));
        assertThat(html.indexOf("class=\"persona-key data-cell strong-cell\""))
                .isLessThan(html.indexOf("class=\"nickname data-cell strong-cell\""));
        assertThat(html.indexOf("class=\"nickname data-cell strong-cell\""))
                .isLessThan(html.indexOf("class=\"badge template-badge"));
        assertThat(html)
                .contains("data-sort-key=\"nickname\"")
                .doesNotContain("persona-details")
                .doesNotContain("persona-title")
                .doesNotContain("""
                        <span>생성 일시</span>
                        <strong>${escapeHtml(formatCreatedAt(persona.createdAt))}</strong>
                    """);
    }

    @Test
    void 상태_템플릿_배지는_역할과_상태에_따라_구분되는_색상_톤을_쓴다() throws IOException {
        String html = consoleHtml();

        assertThat(html)
                .contains("templateBadgeClass(persona.template)")
                .contains("template-badge--consumer-positive")
                .contains("template-badge--consumer-negative")
                .contains("template-badge--instructor-pending")
                .contains("template-badge--instructor-positive");
        assertThat(html.indexOf(".template-badge--consumer-positive"))
                .isLessThan(html.indexOf(".template-badge--instructor-positive"));
        assertThat(html)
                .contains("rgba(142, 232, 216")
                .contains("rgba(122, 162, 255")
                .contains("rgba(255, 177, 153");
    }

    private static String consoleHtml() throws IOException {
        return new String(
                new ClassPathResource("dev-auth-console.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }
}
