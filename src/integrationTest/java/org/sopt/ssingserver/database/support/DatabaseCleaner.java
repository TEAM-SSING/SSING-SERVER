package org.sopt.ssingserver.database.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 애플리케이션 데이터만 지우고 Flyway 이력과 migration 소유 기준 데이터는 보존한다. */
public final class DatabaseCleaner {

    private static final Set<String> PRESERVED_TABLES = Set.of(
            "flyway_schema_history",
            "platform_fee_policies"
    );

    private DatabaseCleaner() {
    }

    public static void clean(DataSource dataSource) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("DatabaseCleaner는 활성 transaction 밖에서만 실행할 수 있습니다.");
        }

        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            List<String> tables = applicationTables(connection);
            boolean foreignKeyChecksDisabled = false;
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                foreignKeyChecksDisabled = true;
                for (String table : tables) {
                    statement.execute("TRUNCATE TABLE " + quoteIdentifier(table));
                }
            } finally {
                if (foreignKeyChecksDisabled) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                    }
                }
            }
            return null;
        });
    }

    public static List<String> applicationTableNames(DataSource dataSource) {
        return new JdbcTemplate(dataSource).execute(
                (ConnectionCallback<List<String>>) DatabaseCleaner::applicationTables
        );
    }

    private static List<String> applicationTables(Connection connection) throws SQLException {
        List<String> allTables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """
        ); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                allTables.add(resultSet.getString("table_name"));
            }
        }

        if (!allTables.containsAll(PRESERVED_TABLES)) {
            throw new IllegalStateException("보존해야 할 Flyway 이력 또는 필수 기준 데이터 테이블이 없습니다.");
        }
        return allTables.stream()
                .filter(table -> !PRESERVED_TABLES.contains(table))
                .toList();
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
