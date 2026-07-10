package com.willa.ai.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Postgres CHECK trên workflow_usages.workflow có thể thiếu giá trị enum mới (vd. BRAND_CHECK).
 */
@Component
@Slf4j
public class WorkflowUsagesConstraintMigration {

    private static final String CONSTRAINT = "workflow_usages_workflow_check";

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    @Transactional
    public void migrate() {
        try {
            if (!tableExists("workflow_usages")) {
                return;
            }
            if (constraintExists(CONSTRAINT)) {
                entityManager.createNativeQuery(
                        "ALTER TABLE workflow_usages DROP CONSTRAINT " + CONSTRAINT
                ).executeUpdate();
                log.info("Dropped legacy {}", CONSTRAINT);
            }
            entityManager.createNativeQuery("""
                    ALTER TABLE workflow_usages ADD CONSTRAINT workflow_usages_workflow_check
                    CHECK (workflow IN (
                      'CHAT',
                      'ANALYZE',
                      'GENERATE',
                      'GENERATE_SEED',
                      'REGEN',
                      'PREPARE_REGEN',
                      'SUGGEST_STYLE',
                      'EXTRACT_LAYERS',
                      'WORKSPACE',
                      'BRAND_CHECK'
                    ))
                    """).executeUpdate();
            log.info("Refreshed {} with BRAND_CHECK", CONSTRAINT);
        } catch (Exception e) {
            log.warn("Workflow usages constraint migration skipped: {}", e.getMessage());
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

    private boolean constraintExists(String constraintName) {
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.table_constraints
                  WHERE table_schema = 'public'
                    AND table_name = 'workflow_usages'
                    AND constraint_name = :name
                )
                """)
                .setParameter("name", constraintName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
