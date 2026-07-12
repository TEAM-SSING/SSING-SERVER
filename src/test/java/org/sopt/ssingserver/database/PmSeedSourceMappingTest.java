package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PmSeedSourceMappingTest {

    private static final Path SOURCE_MAPPING = Path.of(
            "db/seed/scenarios/pm-full-requested-catalog/source-mapping.tsv"
    );

    private static final Map<String, Long> EXPECTED_ROW_COUNTS = Map.of(
            "resort", 9L,
            "member", 13L,
            "matching_request", 16L,
            "matching_request_participant", 49L,
            "instructor_profile", 4L,
            "instructor_matching_setting", 4L,
            "instructor_price_policy", 4L,
            "instructor_certification", 6L
    );

    private static final Set<String> ALLOWED_DISPOSITIONS = Set.of(
            "MAPPED",
            "MAPPED_REVIEW_REQUIRED",
            "EXCLUDED_UNSUPPORTED"
    );

    @Test
    void PM_스프레드시트_105개_source_row는_모두_한번씩_추적한다() throws IOException {
        List<SourceMapping> mappings = readMappings();

        assertThat(mappings).hasSize(105);
        assertThat(mappings.stream().collect(Collectors.groupingBy(SourceMapping::sourceType, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_ROW_COUNTS);
        assertThat(mappings)
                .extracting(SourceMapping::disposition)
                .allMatch(ALLOWED_DISPOSITIONS::contains);

        Set<String> uniqueSourceRows = new HashSet<>();
        mappings.forEach(mapping -> assertThat(uniqueSourceRows.add(mapping.sourceType() + ":" + mapping.sourceKey()))
                .as("duplicate source row: %s:%s", mapping.sourceType(), mapping.sourceKey())
                .isTrue());
    }

    @Test
    void 현재_enum으로_표현할_수_없는_자격_두건만_명시적으로_제외한다() throws IOException {
        List<SourceMapping> excluded = readMappings().stream()
                .filter(mapping -> mapping.disposition().equals("EXCLUDED_UNSUPPORTED"))
                .toList();

        assertThat(excluded)
                .extracting(SourceMapping::sourceKey)
                .containsExactlyInAnyOrder(
                        "instructor_certification_003",
                        "instructor_certification_005"
                );
        assertThat(excluded)
                .allMatch(mapping -> mapping.target().equals("-") && !mapping.reason().isBlank());
    }

    @Test
    void 실행_seed는_persona_raw를_저장하지_않는다() throws IOException {
        String seedSql = Files.readString(Path.of(
                "db/seed/scenarios/pm-full-requested-catalog/seed.sql"
        ));

        assertThat(seedSql).doesNotContain("persona_raw");
    }

    private List<SourceMapping> readMappings() throws IOException {
        List<String> lines = Files.readAllLines(SOURCE_MAPPING);
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).isEqualTo("source_type\tsource_key\tdisposition\ttarget\treason");

        return lines.stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(this::parse)
                .toList();
    }

    private SourceMapping parse(String line) {
        String[] columns = line.split("\\t", -1);
        assertThat(columns)
                .as("invalid source mapping row: %s", line)
                .hasSize(5);
        assertThat(columns[0]).isNotBlank();
        assertThat(columns[1]).isNotBlank();
        assertThat(columns[2]).isNotBlank();
        assertThat(columns[3]).isNotBlank();

        return new SourceMapping(columns[0], columns[1], columns[2], columns[3], columns[4]);
    }

    private record SourceMapping(
            String sourceType,
            String sourceKey,
            String disposition,
            String target,
            String reason
    ) {
    }
}
