package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class TaskDeadlineNotificationsMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (tableExists("task_deadline_notifications")) {
                return;
            }
            entityManager.createNativeQuery("""
                    CREATE TABLE task_deadline_notifications (
                      id BIGSERIAL PRIMARY KEY,
                      task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                      notification_type VARCHAR(20) NOT NULL,
                      due_date TIMESTAMP NOT NULL,
                      sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
                      CONSTRAINT uq_task_deadline_notification UNIQUE (task_id, notification_type, due_date)
                    )
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE INDEX idx_task_deadline_notifications_task ON task_deadline_notifications(task_id)
                    """).executeUpdate();
            log.info("Created task_deadline_notifications table");
        } catch (Exception e) {
            log.warn("Task deadline notifications migration skipped: {}", e.getMessage());
        }
    }

    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = :tableName
                """)
                .setParameter("tableName", tableName)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
