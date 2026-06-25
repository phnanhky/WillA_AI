package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gói workspace riêng (Free / Student / Pro Workspace) — mặc định FREE cho mọi user. */
@Component
@Slf4j
public class WorkspacePlanTierMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (!columnExists("users", "workspace_plan_tier")) {
                entityManager.createNativeQuery("""
                        ALTER TABLE users ADD COLUMN workspace_plan_tier VARCHAR(32) NOT NULL DEFAULT 'FREE_WORKSPACE'
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        UPDATE users SET workspace_plan_tier = 'FREE_WORKSPACE' WHERE workspace_plan_tier IS NULL
                        """).executeUpdate();
                log.info("Added users.workspace_plan_tier column (default FREE_WORKSPACE)");
            }
        } catch (Exception e) {
            log.warn("Workspace plan tier migration skipped: {}", e.getMessage());
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
