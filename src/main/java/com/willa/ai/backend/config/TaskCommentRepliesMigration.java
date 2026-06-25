package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Thêm parent_comment_id cho reply thread trên task comments. */
@Component
@Slf4j
public class TaskCommentRepliesMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (!columnExists("task_comments", "parent_comment_id")) {
                entityManager.createNativeQuery("""
                        ALTER TABLE task_comments
                        ADD COLUMN parent_comment_id BIGINT REFERENCES task_comments(id) ON DELETE SET NULL
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX IF NOT EXISTS idx_task_comments_parent_id
                        ON task_comments(parent_comment_id)
                        """).executeUpdate();
                log.info("Added task_comments.parent_comment_id column");
            }
        } catch (Exception e) {
            log.warn("Task comment replies migration skipped: {}", e.getMessage());
        }
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
