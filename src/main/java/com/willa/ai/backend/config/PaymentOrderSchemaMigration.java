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

/**
 * Cột buyer trên payments + student ID card trên users (ddl-auto=validate).
 */
@Configuration
@Slf4j
public class PaymentOrderSchemaMigration {

    /** Khác tên class bean (`paymentOrderSchemaMigration`) để tránh BeanDefinitionOverrideException. */
    private static final String MIGRATION_BEAN = "paymentOrderBuyerSchemaMigration";

    @Bean(name = MIGRATION_BEAN)
    public String migratePaymentOrderSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (tableExists(connection, "payments")) {
                addColumnIfMissing(connection, statement, "payments", "buyer_first_name", "VARCHAR(120)");
                addColumnIfMissing(connection, statement, "payments", "buyer_last_name", "VARCHAR(120)");
                addColumnIfMissing(connection, statement, "payments", "buyer_phone", "VARCHAR(40)");
                addColumnIfMissing(connection, statement, "payments", "buyer_email", "VARCHAR(255)");
            }
            if (tableExists(connection, "users")) {
                addColumnIfMissing(connection, statement, "users", "student_id_card_url", "TEXT");
            }
            return "migrated";
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate payment/student order schema", e);
        }
    }

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnPaymentOrderMigration() {
        return beanFactory -> ensureDepends(beanFactory, MIGRATION_BEAN);
    }

    private static void ensureDepends(ConfigurableListableBeanFactory beanFactory, String migrationBean) {
        if (!beanFactory.containsBeanDefinition("entityManagerFactory")) {
            return;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition("entityManagerFactory");
        String[] dependsOn = definition.getDependsOn();
        if (dependsOn != null) {
            for (String name : dependsOn) {
                if (migrationBean.equals(name)) {
                    return;
                }
            }
        }
        if (dependsOn == null || dependsOn.length == 0) {
            definition.setDependsOn(migrationBean);
            return;
        }
        String[] next = new String[dependsOn.length + 1];
        System.arraycopy(dependsOn, 0, next, 0, dependsOn.length);
        next[dependsOn.length] = migrationBean;
        definition.setDependsOn(next);
    }

    private static void addColumnIfMissing(
            Connection connection, Statement statement, String table, String column, String sqlType)
            throws SQLException {
        if (!columnExists(connection, table, column)) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
            log.info("Added {}.{}", table, column);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = connection.getMetaData().getTables(null, null, table.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column.toLowerCase())) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }
}
