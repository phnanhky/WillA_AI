package com.willa.ai.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tạo / nâng cấp bảng workspace_experts trước Hibernate validate
 * (prod ddl-auto=validate). Trước đây chạy ApplicationReadyEvent → quá muộn.
 */
@Configuration
@Slf4j
public class WorkspaceExpertsMigration {

    private static final String MIGRATION_BEAN = "workspaceExpertsSchemaMigration";

    @Bean(name = MIGRATION_BEAN)
    public String migrateWorkspaceExpertsSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "workspace_experts")) {
                statement.execute("""
                        CREATE TABLE workspace_experts (
                          id BIGSERIAL PRIMARY KEY,
                          workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
                          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          expertise VARCHAR(500),
                          bio TEXT,
                          headline VARCHAR(200),
                          portfolio_url VARCHAR(500),
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,
                          review_price BIGINT,
                          hourly_rate BIGINT,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("CREATE INDEX idx_workspace_experts_workspace ON workspace_experts(workspace_id)");
                statement.execute("""
                        CREATE UNIQUE INDEX uq_workspace_experts_ws_user
                        ON workspace_experts (workspace_id, user_id) WHERE workspace_id IS NOT NULL
                        """);
                statement.execute("""
                        CREATE UNIQUE INDEX uq_workspace_experts_platform_user
                        ON workspace_experts (user_id) WHERE workspace_id IS NULL
                        """);
                log.info("Created workspace_experts table");
            } else {
                upgradeExistingTable(connection, statement);
            }
        } catch (SQLException e) {
            log.warn("Workspace experts migration skipped: {}", e.getMessage());
        }
        return "ok";
    }

    private static void upgradeExistingTable(Connection connection, Statement statement) throws SQLException {
        if (columnExists(connection, "workspace_experts", "workspace_id")) {
            try {
                statement.execute("ALTER TABLE workspace_experts ALTER COLUMN workspace_id DROP NOT NULL");
            } catch (SQLException ignored) {
                /* already nullable */
            }
        }
        if (constraintExists(connection, "workspace_experts", "workspace_experts_workspace_id_user_id_key")) {
            statement.execute(
                    "ALTER TABLE workspace_experts DROP CONSTRAINT workspace_experts_workspace_id_user_id_key");
        }
        if (!indexExists(connection, "uq_workspace_experts_ws_user")) {
            statement.execute("""
                    CREATE UNIQUE INDEX uq_workspace_experts_ws_user
                    ON workspace_experts (workspace_id, user_id) WHERE workspace_id IS NOT NULL
                    """);
        }
        if (!indexExists(connection, "uq_workspace_experts_platform_user")) {
            statement.execute("""
                    CREATE UNIQUE INDEX uq_workspace_experts_platform_user
                    ON workspace_experts (user_id) WHERE workspace_id IS NULL
                    """);
        }
        if (!columnExists(connection, "workspace_experts", "review_price")) {
            statement.execute("ALTER TABLE workspace_experts ADD COLUMN review_price BIGINT");
            log.info("Added workspace_experts.review_price");
        } else {
            relaxReviewPriceColumn(statement);
        }
        if (!columnExists(connection, "workspace_experts", "hourly_rate")) {
            statement.execute("ALTER TABLE workspace_experts ADD COLUMN hourly_rate BIGINT");
            log.info("Added workspace_experts.hourly_rate");
        }
        if (!columnExists(connection, "workspace_experts", "headline")) {
            statement.execute("ALTER TABLE workspace_experts ADD COLUMN headline VARCHAR(200)");
            log.info("Added workspace_experts.headline");
        }
        if (!columnExists(connection, "workspace_experts", "portfolio_url")) {
            statement.execute("ALTER TABLE workspace_experts ADD COLUMN portfolio_url VARCHAR(500)");
            log.info("Added workspace_experts.portfolio_url");
        }
        migrateToAppWideExperts(statement);
        log.info("Upgraded workspace_experts for app-wide experts");
    }

    /** Expert hỗ trợ toàn app — bỏ gắn workspace, gộp trùng user. */
    private static void migrateToAppWideExperts(Statement statement) throws SQLException {
        statement.execute("""
                DELETE FROM workspace_experts w
                WHERE w.workspace_id IS NOT NULL
                  AND EXISTS (
                    SELECT 1 FROM workspace_experts p
                    WHERE p.user_id = w.user_id AND p.workspace_id IS NULL
                  )
                """);
        statement.execute("""
                DELETE FROM workspace_experts w
                WHERE w.workspace_id IS NOT NULL
                  AND w.id NOT IN (
                    SELECT MIN(w2.id) FROM workspace_experts w2
                    WHERE w2.workspace_id IS NOT NULL
                    GROUP BY w2.user_id
                  )
                """);
        statement.execute("UPDATE workspace_experts SET workspace_id = NULL WHERE workspace_id IS NOT NULL");
        statement.execute("""
                DELETE FROM workspace_experts w
                WHERE w.id NOT IN (
                  SELECT MIN(w2.id) FROM workspace_experts w2 GROUP BY w2.user_id
                )
                """);
        log.info("Migrated experts to app-wide scope");
    }

    private static void relaxReviewPriceColumn(Statement statement) {
        try {
            statement.execute("ALTER TABLE workspace_experts ALTER COLUMN review_price DROP DEFAULT");
        } catch (SQLException ignored) {
            /* no default */
        }
        try {
            statement.execute("ALTER TABLE workspace_experts ALTER COLUMN review_price DROP NOT NULL");
        } catch (SQLException ignored) {
            /* already nullable */
        }
    }

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnWorkspaceExpertsMigration() {
        return WorkspaceExpertsMigration::ensureEntityManagerDependsOnMigration;
    }

    private static void ensureEntityManagerDependsOnMigration(ConfigurableListableBeanFactory beanFactory) {
        if (!beanFactory.containsBeanDefinition("entityManagerFactory")) {
            return;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition("entityManagerFactory");
        String[] dependsOn = definition.getDependsOn();
        if (dependsOn != null) {
            for (String name : dependsOn) {
                if (MIGRATION_BEAN.equals(name)) {
                    return;
                }
            }
        }
        if (dependsOn == null || dependsOn.length == 0) {
            definition.setDependsOn(MIGRATION_BEAN);
            return;
        }
        String[] next = new String[dependsOn.length + 1];
        System.arraycopy(dependsOn, 0, next, 0, dependsOn.length);
        next[dependsOn.length] = MIGRATION_BEAN;
        definition.setDependsOn(next);
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = ?
                )
                """)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'public'
                    AND table_name = ?
                    AND column_name = ?
                )
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean constraintExists(Connection connection, String tableName, String constraintName)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.table_constraints
                  WHERE table_schema = 'public'
                    AND table_name = ?
                    AND constraint_name = ?
                )
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean indexExists(Connection connection, String indexName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM pg_indexes
                  WHERE schemaname = 'public' AND indexname = ?
                )
                """)) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
