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

/** Nâng cấp schema coupons trước Hibernate validate (prod ddl-auto=validate). */
@Configuration
@Slf4j
public class CouponSchemaMigration {

    private static final String MIGRATION_BEAN = "couponsSchemaMigration";

    @Bean(name = MIGRATION_BEAN)
    public String migrateCouponSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "coupons")) {
                return "ok";
            }
            addColumnIfMissing(connection, statement, "bonus_days", "INTEGER");
            addColumnIfMissing(connection, statement, "starts_at", "TIMESTAMP");
            if (!columnExists(connection, "coupons", "max_redemptions")) {
                statement.execute("ALTER TABLE coupons ADD COLUMN max_redemptions INTEGER");
                statement.execute("UPDATE coupons SET max_redemptions = 1");
                log.info("Added coupons.max_redemptions with default single-use");
            }
            addColumnIfMissing(connection, statement, "redemption_count", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "allowed_user_ids", "TEXT");
            addColumnIfMissing(connection, statement, "eligible_plans", "TEXT");
            statement.execute("""
                    UPDATE coupons
                    SET redemption_count = 1
                    WHERE redeemed_at IS NOT NULL
                      AND (redemption_count IS NULL OR redemption_count = 0)
                    """);
        } catch (SQLException e) {
            log.warn("Coupon schema migration skipped: {}", e.getMessage());
        }
        return "ok";
    }

    private static void addColumnIfMissing(
            Connection connection, Statement statement, String column, String sqlType) throws SQLException {
        if (!columnExists(connection, "coupons", column)) {
            statement.execute("ALTER TABLE coupons ADD COLUMN " + column + " " + sqlType);
            log.info("Added coupons.{}", column);
        }
    }

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnCouponMigration() {
        return CouponSchemaMigration::ensureEntityManagerDependsOnMigration;
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
        String[] updated = new String[dependsOn.length + 1];
        System.arraycopy(dependsOn, 0, updated, 0, dependsOn.length);
        updated[dependsOn.length] = MIGRATION_BEAN;
        definition.setDependsOn(updated);
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
}
