package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bổ sung schema workspace/channel cho production (ddl-auto có thể là validate/none).
 */
@Component
@Slf4j
@Order(50)
public class WorkspaceChannelSchemaMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            migrateWorkspaceMembers();
            migrateChannelMessages();
            migrateChecklistItemAssignees();
            log.info("Workspace channel schema migration completed");
        } catch (Exception e) {
            log.error("Workspace channel schema migration failed: {}", e.getMessage(), e);
        }
    }

    private void migrateWorkspaceMembers() {
        if (!tableExists("workspace_members")) {
            return;
        }
        addColumnIfMissing("workspace_members", "join_source", "VARCHAR(20)");
        addColumnIfMissing("workspace_members", "first_active_at", "TIMESTAMP");
        addColumnIfMissing("workspace_members", "last_active_at", "TIMESTAMP");
    }

    private void migrateChannelMessages() {
        if (!tableExists("channel_messages")) {
            return;
        }
        addColumnIfMissing("channel_messages", "parent_message_id", "BIGINT");
        if (!fkExists("channel_messages", "fk_channel_messages_parent")) {
            try {
                entityManager.createNativeQuery("""
                        ALTER TABLE channel_messages
                        ADD CONSTRAINT fk_channel_messages_parent
                        FOREIGN KEY (parent_message_id) REFERENCES channel_messages(id) ON DELETE CASCADE
                        """).executeUpdate();
            } catch (Exception e) {
                log.warn("Could not add parent_message FK (may already exist): {}", e.getMessage());
            }
        }
        if (!columnExists("channel_messages", "message_kind")) {
            entityManager.createNativeQuery("""
                    ALTER TABLE channel_messages
                    ADD COLUMN message_kind VARCHAR(20) NOT NULL DEFAULT 'USER'
                    """).executeUpdate();
        } else {
            entityManager.createNativeQuery("""
                    UPDATE channel_messages SET message_kind = 'USER' WHERE message_kind IS NULL
                    """).executeUpdate();
        }
        addColumnIfMissing("channel_messages", "image_url", "TEXT");
        upgradeColumnToText("channel_messages", "image_url");
        addColumnIfMissing("channel_messages", "tool_result_json", "TEXT");
        try {
            entityManager.createNativeQuery("""
                    ALTER TABLE channel_messages ALTER COLUMN user_id DROP NOT NULL
                    """).executeUpdate();
        } catch (Exception e) {
            log.debug("channel_messages.user_id already nullable: {}", e.getMessage());
        }
        if (!indexExists("idx_channel_messages_parent")) {
            entityManager.createNativeQuery("""
                    CREATE INDEX idx_channel_messages_parent ON channel_messages(parent_message_id)
                    """).executeUpdate();
        }
    }

    private void migrateChecklistItemAssignees() {
        if (!tableExists("task_checklist_item_assignees")) {
            entityManager.createNativeQuery("""
                    CREATE TABLE task_checklist_item_assignees (
                      item_id BIGINT NOT NULL REFERENCES task_checklist_items(id) ON DELETE CASCADE,
                      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                      PRIMARY KEY (item_id, user_id)
                    )
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE INDEX idx_checklist_item_assignees_user ON task_checklist_item_assignees(user_id)
                    """).executeUpdate();
        }
    }

    private void addColumnIfMissing(String table, String column, String sqlType) {
        if (!columnExists(table, column)) {
            entityManager.createNativeQuery(
                    "ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType
            ).executeUpdate();
            log.info("Added column {}.{}", table, column);
        }
    }

    private void upgradeColumnToText(String table, String column) {
        if (!columnExists(table, column)) {
            return;
        }
        try {
            entityManager.createNativeQuery(
                    "ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE TEXT"
            ).executeUpdate();
            log.info("Upgraded {}.{} to TEXT", table, column);
        } catch (Exception e) {
            log.debug("{}.{} already TEXT: {}", table, column, e.getMessage());
        }
    }

    private boolean tableExists(String tableName) {
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = :table
                )
                """)
                .setParameter("table", tableName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private boolean columnExists(String tableName, String columnName) {
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'public'
                    AND table_name = :table
                    AND column_name = :column
                )
                """)
                .setParameter("table", tableName)
                .setParameter("column", columnName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private boolean indexExists(String indexName) {
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM pg_indexes
                  WHERE schemaname = 'public' AND indexname = :name
                )
                """)
                .setParameter("name", indexName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private boolean fkExists(String tableName, String constraintName) {
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.table_constraints
                  WHERE table_schema = 'public'
                    AND table_name = :table
                    AND constraint_name = :name
                    AND constraint_type = 'FOREIGN KEY'
                )
                """)
                .setParameter("table", tableName)
                .setParameter("name", constraintName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
