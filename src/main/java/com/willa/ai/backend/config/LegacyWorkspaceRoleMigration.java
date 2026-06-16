package com.willa.ai.backend.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/** Chuẩn hóa role cũ (ADMIN/EDITOR/VIEWER) → OWNER/MEMBER trên DB hiện có. */
@Component
@Slf4j
public class LegacyWorkspaceRoleMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyRoles() {
        int owners = entityManager.createNativeQuery("""
                UPDATE workspace_members wm
                SET role = 'OWNER'
                FROM workspaces w
                WHERE w.id = wm.workspace_id
                  AND w.owner_id = wm.user_id
                  AND wm.role <> 'OWNER'
                """)
                .executeUpdate();

        int members = entityManager.createNativeQuery("""
                UPDATE workspace_members
                SET role = 'MEMBER'
                WHERE role NOT IN ('OWNER', 'MEMBER')
                """)
                .executeUpdate();

        int invites = migrateInviteRoles();

        if (owners > 0 || members > 0 || invites > 0) {
            log.info(
                    "Migrated legacy workspace roles: {} owners, {} members, {} invites",
                    owners,
                    members,
                    invites);
        }
    }

    private int migrateInviteRoles() {
        Object exists = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = 'workspace_invites'
                )
                """)
                .getSingleResult();
        if (!Boolean.TRUE.equals(exists)) {
            return 0;
        }
        return entityManager.createNativeQuery("""
                UPDATE workspace_invites
                SET role = 'MEMBER'
                WHERE role NOT IN ('OWNER', 'MEMBER')
                """)
                .executeUpdate();
    }
}
