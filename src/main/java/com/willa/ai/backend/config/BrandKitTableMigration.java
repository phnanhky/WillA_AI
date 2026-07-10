package com.willa.ai.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@Slf4j
public class BrandKitTableMigration {

    private static final String MIGRATION_BEAN = "brandKitSchemaMigration";

    @Bean(name = MIGRATION_BEAN)
    public String migrateBrandKitTables(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "brand_kit_profiles")) {
                statement.execute("""
                        CREATE TABLE brand_kit_profiles (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL,
                          title VARCHAR(200) NOT NULL,
                          visual_dna_json TEXT,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          last_checked_at TIMESTAMP
                        )
                        """);
                statement.execute("""
                        CREATE INDEX idx_brand_kit_profiles_user ON brand_kit_profiles(user_id)
                        """);
                log.info("Created brand_kit_profiles table");
            }

            if (!tableExists(connection, "brand_kit_reference_images")) {
                statement.execute("""
                        CREATE TABLE brand_kit_reference_images (
                          id BIGSERIAL PRIMARY KEY,
                          profile_id BIGINT NOT NULL REFERENCES brand_kit_profiles(id) ON DELETE CASCADE,
                          image_url TEXT NOT NULL,
                          file_name VARCHAR(500),
                          file_size_bytes BIGINT NOT NULL DEFAULT 0,
                          sort_order INTEGER NOT NULL DEFAULT 0,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("""
                        CREATE INDEX idx_brand_kit_ref_images_profile ON brand_kit_reference_images(profile_id)
                        """);
                log.info("Created brand_kit_reference_images table");
            }

            if (!tableExists(connection, "brand_kit_checks")) {
                statement.execute("""
                        CREATE TABLE brand_kit_checks (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          profile_id BIGINT REFERENCES brand_kit_profiles(id) ON DELETE SET NULL,
                          status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
                          avg_brand_score NUMERIC(5,1),
                          total_assets INTEGER NOT NULL DEFAULT 0,
                          report_json TEXT NOT NULL,
                          error_message TEXT,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("""
                        CREATE INDEX idx_brand_kit_checks_user ON brand_kit_checks(user_id)
                        """);
                statement.execute("""
                        CREATE INDEX idx_brand_kit_checks_profile ON brand_kit_checks(profile_id)
                        """);
                log.info("Created brand_kit_checks table");
            }

            if (!tableExists(connection, "brand_kit_check_assets")) {
                statement.execute("""
                        CREATE TABLE brand_kit_check_assets (
                          id BIGSERIAL PRIMARY KEY,
                          check_id BIGINT NOT NULL REFERENCES brand_kit_checks(id) ON DELETE CASCADE,
                          image_url TEXT NOT NULL,
                          file_name VARCHAR(500),
                          brand_score NUMERIC(5,1),
                          severity VARCHAR(20),
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("""
                        CREATE INDEX idx_brand_kit_check_assets_check ON brand_kit_check_assets(check_id)
                        """);
                log.info("Created brand_kit_check_assets table");
            }

            return "migrated";
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate brand kit tables", e);
        }
    }

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnBrandKitMigration() {
        return BrandKitTableMigration::ensureEntityManagerDependsOnMigration;
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
        try (var ps = connection.prepareStatement("""
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
}
