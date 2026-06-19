package com.willa.ai.backend.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/** Tạo bảng workspace_projects và thêm project_id vào tasks. */
@Component
@Slf4j
public class WorkspaceProjectsMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (!tableExists("workspace_projects")) {
                applySchema();
            } else if (!columnExists("tasks", "project_id")) {
                addProjectIdColumn();
            }
        } catch (Exception e) {
            log.warn("Workspace projects migration skipped: {}", e.getMessage());
        }
    }

    private void applySchema() {
        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS workspace_projects (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
                    name VARCHAR(120) NOT NULL,
                    position INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (workspace_id, name)
                )
                """).executeUpdate();

        entityManager.createNativeQuery("""
                CREATE INDEX IF NOT EXISTS idx_workspace_projects_workspace_id
                ON workspace_projects(workspace_id, position)
                """).executeUpdate();

        addProjectIdColumn();
        log.info("Created workspace_projects table and tasks.project_id column");
    }

    private void addProjectIdColumn() {
        if (columnExists("tasks", "project_id")) {
            return;
        }
        entityManager.createNativeQuery("""
                ALTER TABLE tasks
                ADD COLUMN project_id BIGINT REFERENCES workspace_projects(id) ON DELETE CASCADE
                """).executeUpdate();
        entityManager.createNativeQuery("""
                CREATE INDEX IF NOT EXISTS idx_tasks_project_id
                ON tasks(project_id, status, position)
                """).executeUpdate();
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
}
