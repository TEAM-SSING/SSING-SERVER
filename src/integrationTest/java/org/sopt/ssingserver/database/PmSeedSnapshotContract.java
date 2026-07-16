package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class PmSeedSnapshotContract {

    private static final Path EXPECTED_SNAPSHOT = Path.of(
            "db/seed/scenarios/pm-full-requested-catalog/expected-snapshot.tsv"
    );
    private static final String HEADER = "aggregate_type\taggregate_key\tsource_keys\texpected_json";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Set<String> ignoredConsumerPersonaKeys;

    private PmSeedSnapshotContract(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Set<String> ignoredConsumerPersonaKeys
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ignoredConsumerPersonaKeys = ignoredConsumerPersonaKeys;
    }

    static void assertMatches(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) throws IOException {
        new PmSeedSnapshotContract(jdbcTemplate, objectMapper, Set.of()).assertMatches();
    }

    static void assertMatchesIgnoringConsumerPersona(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            String ignoredPersonaKey
    ) throws IOException {
        new PmSeedSnapshotContract(
                jdbcTemplate,
                objectMapper,
                Set.of(ignoredPersonaKey)
        ).assertMatches();
    }

    // DB에 원본 source key를 저장하지 않으므로 가변 ID·시각을 뺀 aggregate 값의 multiset을 비교한다.
    private void assertMatches() throws IOException {
        List<ExpectedAggregate> expected = readExpectedAggregates();
        Set<String> pmResortCodes = Set.copyOf(expected.stream()
                .filter(aggregate -> aggregate.type().equals("RESORT"))
                .map(aggregate -> aggregate.json().path("code").asText())
                .toList());
        List<ActualAggregate> actual = new ArrayList<>();
        // Base-only 리조트는 PM 시트의 9개 원본 aggregate 계약에 포함하지 않는다.
        actual.addAll(readActualResorts().stream()
                .filter(aggregate -> pmResortCodes.contains(aggregate.json().path("code").asText()))
                .toList());
        actual.addAll(readActualConsumers().stream()
                .filter(aggregate -> !ignoredConsumerPersonaKeys.contains(
                        aggregate.json().path("personaKey").asText()
                ))
                .toList());
        actual.addAll(readActualInstructors());
        actual.addAll(readActualRequests());

        for (String aggregateType : List.of("RESORT", "CONSUMER", "INSTRUCTOR", "REQUEST")) {
            List<JsonNode> expectedJson = expected.stream()
                    .filter(aggregate -> aggregate.type().equals(aggregateType))
                    .map(ExpectedAggregate::json)
                    .toList();
            List<JsonNode> actualJson = actual.stream()
                    .filter(aggregate -> aggregate.type().equals(aggregateType))
                    .map(ActualAggregate::json)
                    .toList();

            assertThat(actualJson)
                    .as("PM seed %s aggregate", aggregateType)
                    .containsExactlyInAnyOrderElementsOf(expectedJson);
        }
    }

    private List<ExpectedAggregate> readExpectedAggregates() throws IOException {
        List<String> lines = Files.readAllLines(EXPECTED_SNAPSHOT);
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).isEqualTo(HEADER);

        List<ExpectedAggregate> aggregates = new ArrayList<>();
        for (String line : lines.stream().skip(1).filter(value -> !value.isBlank()).toList()) {
            String[] columns = line.split("\\t", -1);
            assertThat(columns)
                    .as("invalid expected snapshot row: %s", line)
                    .hasSize(4);
            aggregates.add(new ExpectedAggregate(
                    columns[0],
                    columns[1],
                    objectMapper.readTree(columns[3])
            ));
        }
        return aggregates;
    }

    private List<ActualAggregate> readActualResorts() {
        return jdbcTemplate.query(
                """
                SELECT code, name, display_name, pass_fee_amount
                FROM resorts
                ORDER BY code
                """,
                (resultSet, rowNumber) -> {
                    ObjectNode json = objectMapper.createObjectNode();
                    json.put("code", resultSet.getString("code"));
                    json.put("name", resultSet.getString("name"));
                    json.put("displayName", resultSet.getString("display_name"));
                    json.put("passFeeAmount", resultSet.getInt("pass_fee_amount"));
                    return new ActualAggregate("RESORT", json);
                }
        );
    }

    private List<ActualAggregate> readActualConsumers() {
        return jdbcTemplate.query(
                """
                SELECT persona.persona_key, persona.template,
                       member.nickname, member.profile_image_url, member.role, member.status
                FROM dev_personas persona
                JOIN members member ON member.id = persona.member_id
                WHERE member.role = 'CONSUMER'
                ORDER BY persona.persona_key
                """,
                (resultSet, rowNumber) -> {
                    ObjectNode json = memberJson(
                            resultSet.getString("persona_key"),
                            resultSet.getString("template"),
                            resultSet.getString("nickname"),
                            resultSet.getString("profile_image_url"),
                            resultSet.getString("role"),
                            resultSet.getString("status")
                    );
                    return new ActualAggregate("CONSUMER", json);
                }
        );
    }

    private List<ActualAggregate> readActualInstructors() {
        return jdbcTemplate.query(
                """
                SELECT persona.persona_key, persona.template,
                       member.nickname, member.profile_image_url, member.role, member.status,
                       resort.code AS resort_code,
                       profile.real_name, profile.phone, profile.gender, profile.birth_date,
                       profile.intro, profile.career_start_date, profile.level, profile.experience,
                       profile.certificate_type, profile.approval_status, profile.approved_at,
                       setting.sport, setting.max_headcount,
                       setting.is_equipment_ready, setting.is_exposed,
                       price.base_price_amount, price.additional_person_price_amount, price.is_active,
                       (SELECT GROUP_CONCAT(level.lesson_level ORDER BY level.lesson_level SEPARATOR ',')
                        FROM instructor_matching_settings_lesson_levels level
                        WHERE level.instructor_matching_setting_id = setting.id) AS lesson_levels,
                       (SELECT GROUP_CONCAT(duration.available_duration_minutes
                                            ORDER BY duration.available_duration_minutes SEPARATOR ',')
                        FROM instructor_matching_settings_available_durations duration
                        WHERE duration.instructor_matching_setting_id = setting.id) AS available_durations,
                       (SELECT GROUP_CONCAT(certificate.certificate_type
                                            ORDER BY certificate.certificate_type SEPARATOR ',')
                        FROM instructor_profile_certificates certificate
                        WHERE certificate.instructor_profile_id = profile.id) AS certificates
                FROM dev_personas persona
                JOIN members member ON member.id = persona.member_id
                JOIN instructor_profiles profile ON profile.member_id = member.id
                JOIN resorts resort ON resort.id = profile.resort_id
                JOIN instructor_matching_settings setting ON setting.instructor_profile_id = profile.id
                JOIN instructor_price_policies price ON price.instructor_profile_id = profile.id
                WHERE member.role = 'INSTRUCTOR'
                ORDER BY persona.persona_key
                """,
                (resultSet, rowNumber) -> {
                    ObjectNode json = memberJson(
                            resultSet.getString("persona_key"),
                            resultSet.getString("template"),
                            resultSet.getString("nickname"),
                            resultSet.getString("profile_image_url"),
                            resultSet.getString("role"),
                            resultSet.getString("status")
                    );

                    ObjectNode profile = json.putObject("profile");
                    profile.put("resortCode", resultSet.getString("resort_code"));
                    profile.put("realName", resultSet.getString("real_name"));
                    profile.put("phone", resultSet.getString("phone"));
                    profile.put("gender", resultSet.getString("gender"));
                    profile.put("birthDate", resultSet.getDate("birth_date").toLocalDate().toString());
                    profile.put("intro", resultSet.getString("intro"));
                    profile.put(
                            "careerStartDate",
                            resultSet.getDate("career_start_date").toLocalDate().toString()
                    );
                    profile.put("level", resultSet.getInt("level"));
                    if (resultSet.getString("certificate_type") == null) {
                        profile.putNull("legacyCertificateType");
                    } else {
                        profile.put("legacyCertificateType", resultSet.getString("certificate_type"));
                    }
                    profile.put("experience", resultSet.getInt("experience"));
                    profile.put("approvalStatus", resultSet.getString("approval_status"));
                    profile.put("approvedAt", format(resultSet.getTimestamp("approved_at")));

                    ObjectNode setting = json.putObject("setting");
                    setting.put("sport", resultSet.getString("sport"));
                    setting.put("maxHeadcount", resultSet.getInt("max_headcount"));
                    setting.put("equipmentReady", resultSet.getBoolean("is_equipment_ready"));
                    setting.put("exposed", resultSet.getBoolean("is_exposed"));
                    addCsv(setting.putArray("lessonLevels"), resultSet.getString("lesson_levels"), false);
                    addCsv(
                            setting.putArray("availableDurations"),
                            resultSet.getString("available_durations"),
                            true
                    );

                    ObjectNode pricePolicy = json.putObject("pricePolicy");
                    pricePolicy.put("basePriceAmount", resultSet.getInt("base_price_amount"));
                    pricePolicy.put(
                            "additionalPersonPriceAmount",
                            resultSet.getInt("additional_person_price_amount")
                    );
                    pricePolicy.put("active", resultSet.getBoolean("is_active"));
                    addCsv(json.putArray("certificates"), resultSet.getString("certificates"), false);

                    return new ActualAggregate("INSTRUCTOR", json);
                }
        );
    }

    private List<ActualAggregate> readActualRequests() {
        return jdbcTemplate.query(
                """
                SELECT request.id, persona.persona_key, resort.code AS resort_code,
                       request.sport, request.lesson_level, request.headcount,
                       request.is_equipment_ready, request.status, request.status_reason,
                       request.expires_at, request.matching_offer_id,
                       (SELECT GROUP_CONCAT(duration.duration_minutes
                                            ORDER BY duration.duration_minutes SEPARATOR ',')
                        FROM matching_requests_requested_duration_minutes duration
                        WHERE duration.matching_request_id = request.id) AS requested_durations,
                       (SELECT GROUP_CONCAT(CONCAT(participant.age, ':', participant.gender)
                                            ORDER BY participant.age, participant.gender, participant.id SEPARATOR ',')
                        FROM matching_request_participants participant
                        WHERE participant.matching_request_id = request.id) AS participants
                FROM matching_requests request
                JOIN dev_personas persona ON persona.member_id = request.member_id
                JOIN resorts resort ON resort.id = request.resort_id
                ORDER BY request.id
                """,
                (resultSet, rowNumber) -> {
                    ObjectNode json = objectMapper.createObjectNode();
                    json.put("ownerPersonaKey", resultSet.getString("persona_key"));
                    json.put("resortCode", resultSet.getString("resort_code"));
                    json.put("sport", resultSet.getString("sport"));
                    json.put("lessonLevel", resultSet.getString("lesson_level"));
                    json.put("headcount", resultSet.getInt("headcount"));
                    json.put("equipmentReady", resultSet.getBoolean("is_equipment_ready"));
                    json.put("status", resultSet.getString("status"));
                    if (resultSet.getString("status_reason") == null) {
                        json.putNull("statusReason");
                    } else {
                        json.put("statusReason", resultSet.getString("status_reason"));
                    }
                    if (resultSet.getTimestamp("expires_at") == null) {
                        json.putNull("expiresAt");
                    } else {
                        json.put("expiresAt", format(resultSet.getTimestamp("expires_at")));
                    }
                    if (resultSet.getObject("matching_offer_id") == null) {
                        json.putNull("matchingOfferId");
                    } else {
                        json.put("matchingOfferId", resultSet.getLong("matching_offer_id"));
                    }
                    addCsv(
                            json.putArray("requestedDurations"),
                            resultSet.getString("requested_durations"),
                            true
                    );
                    addParticipants(json.putArray("participants"), resultSet.getString("participants"));
                    return new ActualAggregate("REQUEST", json);
                }
        );
    }

    private ObjectNode memberJson(
            String personaKey,
            String template,
            String nickname,
            String profileImageUrl,
            String role,
            String status
    ) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("personaKey", personaKey);
        json.put("nickname", nickname);
        if (profileImageUrl == null) {
            json.putNull("profileImageUrl");
        } else {
            json.put("profileImageUrl", profileImageUrl);
        }
        json.put("role", role);
        json.put("status", status);
        json.put("template", template);
        return json;
    }

    private void addCsv(ArrayNode target, String csv, boolean number) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String value : csv.split(",")) {
            if (number) {
                target.add(Integer.parseInt(value));
            } else {
                target.add(value);
            }
        }
    }

    private void addParticipants(ArrayNode target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String participant : csv.split(",")) {
            String[] values = participant.split(":", -1);
            ObjectNode json = target.addObject();
            json.put("age", Integer.parseInt(values[0]));
            json.put("gender", values[1]);
        }
    }

    private String format(Timestamp timestamp) {
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private record ExpectedAggregate(String type, String key, JsonNode json) {
    }

    private record ActualAggregate(String type, JsonNode json) {
    }
}
