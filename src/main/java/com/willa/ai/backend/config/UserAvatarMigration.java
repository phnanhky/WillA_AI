package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Thêm avatar_url cho ảnh đại diện user (lưu URL R2). */
@Component
@Slf4j
public class UserAvatarMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (!columnExists("users", "avatar_url")) {
                entityManager.createNativeQuery("""
                        ALTER TABLE users ADD COLUMN avatar_url TEXT
                        """).executeUpdate();
                log.info("Added users.avatar_url column");
            }
        } catch (Exception e) {
            log.warn("User avatar migration skipped: {}", e.getMessage());
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
