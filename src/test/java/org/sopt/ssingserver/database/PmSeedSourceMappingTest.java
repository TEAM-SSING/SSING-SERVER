package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PmSeedSourceMappingTest {

    private static final Path SOURCE_MAPPING = Path.of(
            "db/seed/scenarios/pm-full-requested-catalog/source-mapping.tsv"
    );
    private static final Path EXPECTED_SNAPSHOT = Path.of(
            "db/seed/scenarios/pm-full-requested-catalog/expected-snapshot.tsv"
    );
    private static final String EXPECTED_SNAPSHOT_HEADER =
            "aggregate_type\taggregate_key\tsource_keys\texpected_json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int EXPECTED_SOURCE_ROWS = 105;
    private static final int EXPECTED_MAPPED_SOURCE_ROWS = 103;

    private static final Map<String, Long> EXPECTED_AGGREGATE_COUNTS = Map.of(
            "RESORT", 9L,
            "CONSUMER", 9L,
            "INSTRUCTOR", 4L,
            "REQUEST", 16L
    );

    private static final Map<String, String> EXPECTED_CONSUMER_NICKNAMES = Map.of(
            "폭룡적-예지-일반강습생", "폭룡적 예지",
            "느좋그자체-예림-일반강습생", "느좋 그 자체 예림",
            "감다살-유빈-일반강습생", "감다살 유빈",
            "야르-선문-일반강습생", "야르 선문",
            "난리자베스-채원-일반강습생", "난리자베스 채원",
            "대뜸GOAT-성빈-일반강습생", "대뜸 GOAT 성빈",
            "도파민풀충-나현-일반강습생", "도파민 풀충 나현",
            "레전드갱신중인-지환-일반강습생", "레전드 갱신 중인 지환",
            "갑차기스러운-예슬-일반강습생", "갑차기스러운 예슬"
    );

    private static final Map<String, String> EXPECTED_INSTRUCTOR_NICKNAMES = Map.of(
            "기세로다해먹는-도연-승인강사", "기세로 다 해먹는 도연",
            "폼미친-성빈-승인강사", "폼 미친 성빈",
            "뉴런공유중인-유정-승인강사", "뉴런 공유 중인 유정",
            "보법다른-유정-승인강사", "보법 다른 유정"
    );

    private static final Set<String> ALLOWED_DISPOSITIONS = Set.of(
            "MAPPED",
            "MAPPED_REVIEW_REQUIRED",
            "EXCLUDED_UNSUPPORTED"
    );

    @Test
    void 반영_가능한_PM_source_row는_snapshot_계약에서_모두_한번씩_추적한다() throws IOException {
        List<SourceMapping> mappings = readMappings();
        List<ExpectedAggregate> aggregates = readExpectedAggregates();

        assertThat(mappings).hasSize(EXPECTED_SOURCE_ROWS);
        assertThat(mappings)
                .extracting(SourceMapping::disposition)
                .allMatch(ALLOWED_DISPOSITIONS::contains);
        assertThat(aggregates.stream().collect(Collectors.groupingBy(ExpectedAggregate::type, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_AGGREGATE_COUNTS);

        Set<String> uniqueSourceRows = new HashSet<>();
        mappings.forEach(mapping -> assertThat(uniqueSourceRows.add(mapping.sourceKey()))
                .as("duplicate source row: %s", mapping.sourceKey())
                .isTrue());

        List<String> mappedSourceKeys = mappings.stream()
                .filter(mapping -> !mapping.disposition().equals("EXCLUDED_UNSUPPORTED"))
                .map(SourceMapping::sourceKey)
                .toList();
        List<String> contractedSourceKeys = aggregates.stream()
                .flatMap(aggregate -> aggregate.sourceKeys().stream())
                .toList();

        assertThat(mappedSourceKeys).hasSize(EXPECTED_MAPPED_SOURCE_ROWS);
        assertThat(contractedSourceKeys).doesNotHaveDuplicates();
        assertThat(contractedSourceKeys).containsExactlyInAnyOrderElementsOf(mappedSourceKeys);
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
    void snapshot_프로필은_익명화된_시연용_형식을_사용한다() throws IOException {
        List<ExpectedAggregate> aggregates = readExpectedAggregates();
        List<ExpectedAggregate> consumers = aggregates.stream()
                .filter(aggregate -> aggregate.type().equals("CONSUMER"))
                .toList();
        List<ExpectedAggregate> instructors = aggregates.stream()
                .filter(aggregate -> aggregate.type().equals("INSTRUCTOR"))
                .toList();

        consumers.forEach(aggregate -> {
            JsonNode json = aggregate.expectedJson();
            assertThat(EXPECTED_CONSUMER_NICKNAMES).containsKey(aggregate.aggregateKey());
            assertThat(json.path("nickname").asText())
                    .isEqualTo(EXPECTED_CONSUMER_NICKNAMES.get(aggregate.aggregateKey()));
            assertThat(json.path("profileImageUrl").isNull()).isTrue();
        });

        instructors.forEach(aggregate -> {
            JsonNode json = aggregate.expectedJson();
            JsonNode profile = json.path("profile");
            assertThat(EXPECTED_INSTRUCTOR_NICKNAMES).containsKey(aggregate.aggregateKey());
            assertThat(json.path("nickname").asText())
                    .isEqualTo(EXPECTED_INSTRUCTOR_NICKNAMES.get(aggregate.aggregateKey()));
            assertThat(profile.path("realName").asText()).isEqualTo(json.path("nickname").asText());
            assertThat(profile.path("gender").asText())
                    .isEqualTo(aggregate.aggregateKey().equals("폼미친-성빈-승인강사") ? "MALE" : "FEMALE");

            if (!aggregate.aggregateKey().equals("보법다른-유정-승인강사")) {
                assertThat(profile.path("phone").asText()).matches("010-0000-\\d{4}");
                assertThat(profile.path("birthDate").asText()).isIn(
                        "2000-01-01",
                        "2000-01-02",
                        "2000-01-03"
                );
            } else {
                assertThat(profile.path("phone").asText()).isEqualTo("010-0000-0000");
                assertThat(profile.path("birthDate").asText()).isEqualTo("2000-01-04");
            }
            assertThat(profile.path("intro").asText())
                    .hasSizeGreaterThanOrEqualTo(20)
                    .doesNotContain("PM", "fixture", "검증", "로컬 전용", "010-");
            assertThat(json.path("profileImageUrl").isNull()).isTrue();
        });

        String seedSql = Files.readString(Path.of(
                "db/seed/scenarios/pm-full-requested-catalog/seed.sql"
        ));

        assertThat(seedSql).doesNotContain("persona_raw");
        assertThat(Files.readString(EXPECTED_SNAPSHOT)).doesNotContain("persona_raw");
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

    private List<ExpectedAggregate> readExpectedAggregates() throws IOException {
        List<String> lines = Files.readAllLines(EXPECTED_SNAPSHOT);
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).isEqualTo(EXPECTED_SNAPSHOT_HEADER);

        List<ExpectedAggregate> aggregates = new ArrayList<>();
        Set<String> aggregateKeys = new HashSet<>();
        for (String line : lines.stream().skip(1).filter(value -> !value.isBlank()).toList()) {
            String[] columns = line.split("\\t", 4);
            assertThat(columns)
                    .as("invalid expected snapshot row: %s", line)
                    .hasSize(4);
            assertThat(aggregateKeys.add(columns[0] + ":" + columns[1]))
                    .as("duplicate aggregate key: %s:%s", columns[0], columns[1])
                    .isTrue();
            aggregates.add(new ExpectedAggregate(
                    columns[0],
                    columns[1],
                    List.of(columns[2].split(",")),
                    OBJECT_MAPPER.readTree(columns[3])
            ));
        }
        return aggregates;
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

    private record ExpectedAggregate(
            String type,
            String aggregateKey,
            List<String> sourceKeys,
            JsonNode expectedJson
    ) {
    }
}
