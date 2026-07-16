package org.sopt.ssingserver.database.support;

import javax.sql.DataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Flyway가 만든 최신 schema에 테스트 공통 base seed만 다시 적용한다. */
public final class BaseSeedLoader {

    private static final String[] BASE_SEED_FILES = {
            "db/seed/base/001_reference_data.sql",
            "db/seed/base/010_dev_personas.sql"
    };

    private BaseSeedLoader() {
    }

    public static void apply(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        for (String path : BASE_SEED_FILES) {
            populator.addScript(new FileSystemResource(path));
        }
        populator.execute(dataSource);
        assertRequiredData(dataSource);
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
                """
                SELECT COUNT(*)
                FROM dev_personas
                WHERE persona_key IN (
                    '대뜸GOAT-성빈-비발디가격결제-강습생',
                    '보법다른-유정-비발디가격결제-강사'
                )
                """,
                Integer.class
        );

        if (activeFeePolicies == null || activeFeePolicies != 1
                || requiredResorts == null || requiredResorts != 11
                || requiredPersonas == null || requiredPersonas != 2) {
            throw new IllegalStateException("migration 필수 데이터 또는 base seed 계약이 충족되지 않았습니다.");
        }
    }
}
