package org.sopt.ssingserver.database.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Flyway가 만든 최신 schema에 테스트 공통 base seed만 다시 적용한다. */
public final class BaseSeedLoader {

    private static final Path BASE_SEED_DIRECTORY = Path.of("db/seed/base");

    private BaseSeedLoader() {
    }

    public static void apply(DataSource dataSource) {
        try (var seedFiles = Files.list(BASE_SEED_DIRECTORY)) {
            seedFiles
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .forEach(path -> applyScript(dataSource, path));
        } catch (IOException exception) {
            throw new IllegalStateException("base seed SQL 목록을 읽지 못했습니다.", exception);
        }
        assertRequiredData(dataSource);
    }

    private static void applyScript(DataSource dataSource, Path path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(path));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    public static void assertRequiredData(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer activeFeePolicies = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_fee_policies WHERE is_active = b'1' AND fee_rate_bps = 0",
                Integer.class
        );
        Integer requiredResorts = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM resorts
                WHERE code IN (
                    'HIGH1',
                    'PHOENIX_PARK',
                    'VIVALDI_PARK',
                    'WELLI_HILLI_PARK',
                    'ELYSIAN_GANGCHON',
                    'OAK_VALLEY',
                    'ALPENSIA',
                    'O2_RESORT',
                    'KONJIAM_RESORT',
                    'JISAN_FOREST_RESORT',
                    'MUJU_DEOGYUSAN_RESORT'
                )
                """,
                Integer.class
        );
        Integer requiredPersonas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dev_personas",
                Integer.class
        );
        Integer idleInstructorSettings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM instructor_matching_settings WHERE is_exposed = b'0'",
                Integer.class
        );
        Integer matchingRequests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_requests",
                Integer.class
        );

        if (activeFeePolicies == null || activeFeePolicies != 1
                || requiredResorts == null || requiredResorts != 11
                || requiredPersonas == null || requiredPersonas != 14
                || idleInstructorSettings == null || idleInstructorSettings != 4
                || matchingRequests == null || matchingRequests != 0) {
            throw new IllegalStateException("migration 필수 데이터 또는 base seed 계약이 충족되지 않았습니다.");
        }
    }
}
