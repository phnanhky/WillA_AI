package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tạo / nâng cấp bảng workspace_experts (workspace nullable = expert platform). */
@Component
@Slf4j
public class WorkspaceExpertsMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (!tableExists("workspace_experts")) {
                entityManager.createNativeQuery("""
                        CREATE TABLE workspace_experts (
                          id BIGSERIAL PRIMARY KEY,
                          workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
                          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          expertise VARCHAR(500),
                          bio TEXT,
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_workspace_experts_workspace ON workspace_experts(workspace_id)
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE UNIQUE INDEX uq_workspace_experts_ws_user
                        ON workspace_experts (workspace_id, user_id) WHERE workspace_id IS NOT NULL
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE UNIQUE INDEX uq_workspace_experts_platform_user
                        ON workspace_experts (user_id) WHERE workspace_id IS NULL
                        """).executeUpdate();
                log.info("Created workspace_experts table");
            } else {
                upgradeExistingTable();
            }
        } catch (Exception e) {
            log.warn("Workspace experts migration skipped: {}", e.getMessage());
        }
    }

    private void upgradeExistingTable() {
        if (columnExists("workspace_experts", "workspace_id")) {
            entityManager.createNativeQuery("""
                    ALTER TABLE workspace_experts ALTER COLUMN workspace_id DROP NOT NULL
                    """).executeUpdate();
        }
        if (constraintExists("workspace_experts", "workspace_experts_workspace_id_user_id_key")) {
            entityManager.createNativeQuery("""
                    ALTER TABLE workspace_experts DROP CONSTRAINT workspace_experts_workspace_id_user_id_key
                    """).executeUpdate();
        }
        if (!indexExists("uq_workspace_experts_ws_user")) {
            entityManager.createNativeQuery("""
                    CREATE UNIQUE INDEX uq_workspace_experts_ws_user
                    ON workspace_experts (workspace_id, user_id) WHERE workspace_id IS NOT NULL
                    """).executeUpdate();
        }
        if (!indexExists("uq_workspace_experts_platform_user")) {
            entityManager.createNativeQuery("""
                    CREATE UNIQUE INDEX uq_workspace_experts_platform_user
                    ON workspace_experts (user_id) WHERE workspace_id IS NULL
                    """).executeUpdate();
        }
        log.info("Upgraded workspace_experts for platform experts");
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

    private boolean constraintExists(String tableName, String constraintName) {
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.table_constraints
                  WHERE table_schema = 'public'
                    AND table_name = :table
                    AND constraint_name = :name
                )
                """)
                .setParameter("table", tableName)
                .setParameter("name", constraintName)
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
}
