package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bảng workspace_plans + gán user.workspace_plan_id (tách khỏi gói feedback). */
@Component
@Slf4j
public class WorkspacePlansTableMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void migrate() {
        try {
            if (!tableExists("workspace_plans")) {
                entityManager.createNativeQuery("""
                        CREATE TABLE workspace_plans (
                          id BIGSERIAL PRIMARY KEY,
                          code VARCHAR(64) NOT NULL UNIQUE,
                          name VARCHAR(255) NOT NULL,
                          description TEXT,
                          price NUMERIC(19,2) NOT NULL DEFAULT 0,
                          billing_cycle VARCHAR(32) NOT NULL DEFAULT 'MONTHLY',
                          discount_percentage DOUBLE PRECISION,
                          promotional_price NUMERIC(19,2),
                          max_owned_workspaces INTEGER,
                          max_members_per_workspace INTEGER,
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,
                          is_default BOOLEAN NOT NULL DEFAULT FALSE,
                          sort_order INTEGER NOT NULL DEFAULT 0,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """).executeUpdate();
                seedDefaultPlans();
                log.info("Created workspace_plans table with defaults");
            } else if (countWorkspacePlans() == 0) {
                seedDefaultPlans();
            }
            ensureProWorkspaceHasPrice();
            if (!columnExists("users", "workspace_plan_id")) {
                entityManager.createNativeQuery("""
                        ALTER TABLE users ADD COLUMN workspace_plan_id BIGINT REFERENCES workspace_plans(id)
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        UPDATE users u
                        SET workspace_plan_id = wp.id
                        FROM workspace_plans wp
                        WHERE wp.code = COALESCE(u.workspace_plan_tier, 'FREE_WORKSPACE')
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        UPDATE users
                        SET workspace_plan_id = (SELECT id FROM workspace_plans WHERE is_default = TRUE LIMIT 1)
                        WHERE workspace_plan_id IS NULL
                        """).executeUpdate();
                log.info("Linked users.workspace_plan_id to workspace_plans");
            }
        } catch (Exception e) {
            log.warn("Workspace plans table migration skipped: {}", e.getMessage());
        }
    }

    private void seedDefaultPlans() {
        insertPlan("FREE_WORKSPACE", "Free Workspace",
                "1 workspace · tối đa 2 thành viên", 0, 1, 2, true, true, 0);
        insertPlan("STUDENT_WORKSPACE", "Student Workspace",
                "3 workspace · tối đa 5 thành viên", 0, 3, 5, true, false, 1);
        insertPlan("PRO_WORKSPACE", "Pro Workspace",
                "Không giới hạn workspace và thành viên", 199_000, null, null, true, false, 2);
    }

    /** Gói Pro cần giá > 0 để PayOS checkout (giống gói Feedback Pro). */
    private void ensureProWorkspaceHasPrice() {
        if (!tableExists("workspace_plans")) {
            return;
        }
        entityManager.createNativeQuery("""
                UPDATE workspace_plans
                SET price = 199000,
                    promotional_price = COALESCE(promotional_price, 199000),
                    updated_at = NOW()
                WHERE code = 'PRO_WORKSPACE'
                  AND price = 0
                  AND (promotional_price IS NULL OR promotional_price = 0)
                """).executeUpdate();
    }

    private void insertPlan(String code, String name, String description, double price,
                            Integer maxWs, Integer maxMembers, boolean active, boolean isDefault, int sort) {
        entityManager.createNativeQuery("""
                INSERT INTO workspace_plans
                  (code, name, description, price, billing_cycle, max_owned_workspaces,
                   max_members_per_workspace, is_active, is_default, sort_order)
                VALUES (:code, :name, :desc, :price, 'MONTHLY', :maxWs, :maxMem, :active, :isDefault, :sort)
                ON CONFLICT (code) DO NOTHING
                """)
                .setParameter("code", code)
                .setParameter("name", name)
                .setParameter("desc", description)
                .setParameter("price", price)
                .setParameter("maxWs", maxWs)
                .setParameter("maxMem", maxMembers)
                .setParameter("active", active)
                .setParameter("isDefault", isDefault)
                .setParameter("sort", sort)
                .executeUpdate();
    }

    private long countWorkspacePlans() {
        Object result = entityManager.createNativeQuery("SELECT COUNT(*) FROM workspace_plans").getSingleResult();
        return result instanceof Number n ? n.longValue() : 0L;
    }

    private boolean tableExists(String tableName) {
        return booleanQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = :table
                )
                """, tableName, null);
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

    private boolean booleanQuery(String sql, String table, String column) {
        var q = entityManager.createNativeQuery(sql).setParameter("table", table);
        if (column != null) q.setParameter("column", column);
        return Boolean.TRUE.equals(q.getSingleResult());
    }
}
