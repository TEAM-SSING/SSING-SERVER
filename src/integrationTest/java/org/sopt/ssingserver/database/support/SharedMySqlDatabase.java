package org.sopt.ssingserver.database.support;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 한 integrationTest JVM에서 MySQL을 한 번만 시작한다.
 *
 * <p>CI의 두 테스트 Runner는 각각 별도 JVM이므로 Runner마다 컨테이너 하나를 사용한다.
 * Testcontainers reuse 기능은 사용하지 않고 JVM 종료 시 Ryuk 정리에 맡긴다.
 */
public final class SharedMySqlDatabase {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_integration_test")
            .withUsername("ssing")
            .withPassword("ssing");

    private static final DataSource DATA_SOURCE;
    private static final Flyway FLYWAY;
    private static final MigrateResult INITIAL_MIGRATION;

    static {
        MYSQL.start();
        DATA_SOURCE = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
        FLYWAY = flywayConfiguration(true).load();
        INITIAL_MIGRATION = FLYWAY.migrate();
        FLYWAY.validate();
    }

    private SharedMySqlDatabase() {
    }

    public static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    public static DataSource dataSource() {
        return DATA_SOURCE;
    }

    public static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(DATA_SOURCE);
    }

    public static Flyway flyway() {
        return FLYWAY;
    }

    public static int initialMigrationsExecuted() {
        return INITIAL_MIGRATION.migrationsExecuted;
    }

    public static Flyway newHistoricalMigrationFlyway(MigrationVersion... targetVersion) {
        FluentConfiguration configuration = flywayConfiguration(false);
        if (targetVersion.length == 1) {
            configuration.target(targetVersion[0]);
        }
        return configuration.load();
    }

    private static FluentConfiguration flywayConfiguration(boolean cleanDisabled) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .failOnMissingLocations(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(cleanDisabled);
    }
}
