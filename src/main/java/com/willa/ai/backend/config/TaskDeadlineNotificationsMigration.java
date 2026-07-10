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
 * Tạo bảng task_deadline_notifications trước khi Hibernate validate schema (prod ddl-auto=validate).
 */
@Configuration
@Slf4j
public class TaskDeadlineNotificationsMigration {

  private static final String MIGRATION_BEAN = "taskDeadlineSchemaMigration";

  @Bean(name = MIGRATION_BEAN)
  public String migrateTaskDeadlineTable(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      if (tableExists(connection, "task_deadline_notifications")) {
        return "skipped";
      }
      try (Statement statement = connection.createStatement()) {
        statement.execute("""
            CREATE TABLE task_deadline_notifications (
              id BIGSERIAL PRIMARY KEY,
              task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
              notification_type VARCHAR(20) NOT NULL,
              due_date TIMESTAMP NOT NULL,
              sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
              CONSTRAINT uq_task_deadline_notification UNIQUE (task_id, notification_type, due_date)
            )
            """);
        statement.execute("""
            CREATE INDEX idx_task_deadline_notifications_task ON task_deadline_notifications(task_id)
            """);
      }
      log.info("Created task_deadline_notifications table");
      return "migrated";
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to migrate task_deadline_notifications table", e);
    }
  }

  @Bean
  public static BeanFactoryPostProcessor entityManagerFactoryDependsOnTaskDeadlineMigration() {
    return TaskDeadlineNotificationsMigration::ensureEntityManagerDependsOnMigration;
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
