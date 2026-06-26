package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bảng workspace_subscriptions + migrate user hiện có (100 năm) + payments.workspace_plan_id. */
@Component
@Slf4j
public class WorkspaceSubscriptionsTableMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    @Transactional
    public void migrate() {
        try {
            if (!tableExists("workspace_plans")) {
                log.warn("workspace_plans missing — skip workspace_subscriptions migration until plans exist");
                return;
            }

            if (!tableExists("workspace_subscriptions")) {
                entityManager.createNativeQuery("""
                        CREATE TABLE workspace_subscriptions (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL REFERENCES users(id),
                          workspace_plan_id BIGINT NOT NULL REFERENCES workspace_plans(id),
                          start_date TIMESTAMP NOT NULL,
                          end_date TIMESTAMP NOT NULL,
                          status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_ws_subscription_user_id ON workspace_subscriptions(user_id)
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_ws_subscription_plan_id ON workspace_subscriptions(workspace_plan_id)
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_ws_subscription_status ON workspace_subscriptions(status)
                        """).executeUpdate();
                log.info("Created workspace_subscriptions table");
            }

            ensurePaymentsWorkspacePlanColumn();
            ensurePaymentsPlanIdNullable();

            backfillExistingUsers();
        } catch (Exception e) {
            log.warn("Workspace subscriptions migration skipped: {}", e.getMessage());
        }
    }

    private void ensurePaymentsWorkspacePlanColumn() {
        if (!tableExists("payments")) {
            return;
        }
        if (!columnExists("payments", "workspace_plan_id")) {
            entityManager.createNativeQuery("""
                    ALTER TABLE payments ADD COLUMN workspace_plan_id BIGINT REFERENCES workspace_plans(id)
                    """).executeUpdate();
            log.info("Extended payments table with workspace_plan_id");
        }
    }

    private void ensurePaymentsPlanIdNullable() {
        if (!tableExists("payments") || !columnExists("payments", "plan_id")) {
            return;
        }
        Object nullable = entityManager.createNativeQuery("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'payments' AND column_name = 'plan_id'
                """).getSingleResult();
        if ("NO".equalsIgnoreCase(String.valueOf(nullable))) {
            entityManager.createNativeQuery("""
                    ALTER TABLE payments ALTER COLUMN plan_id DROP NOT NULL
                    """).executeUpdate();
            log.info("payments.plan_id is now nullable (required for WORKSPACE checkout)");
        }
    }

    private void backfillExistingUsers() {
        if (!tableExists("workspace_plans")) {
            return;
        }
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO workspace_subscriptions (user_id, workspace_plan_id, start_date, end_date, status)
                SELECT u.id,
                       COALESCE(u.workspace_plan_id, (SELECT id FROM workspace_plans WHERE is_default = TRUE LIMIT 1)),
                       NOW(),
                       NOW() + INTERVAL '100 years',
                       'ACTIVE'
                FROM users u
                WHERE NOT EXISTS (
                  SELECT 1 FROM workspace_subscriptions ws
                  WHERE ws.user_id = u.id AND ws.status = 'ACTIVE'
                )
                """).executeUpdate();
        if (inserted > 0) {
            log.info("Backfilled {} workspace subscriptions (100-year) for existing users", inserted);
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
                  WHERE table_schema = 'public' AND table_name = :table AND column_name = :column
                )
                """)
                .setParameter("table", tableName)
                .setParameter("column", columnName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
