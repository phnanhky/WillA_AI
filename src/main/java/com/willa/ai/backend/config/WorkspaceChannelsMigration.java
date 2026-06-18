package com.willa.ai.backend.config;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.willa.ai.backend.entity.Workspace;
import com.willa.ai.backend.repository.WorkspaceRepository;
import com.willa.ai.backend.service.WorkspaceChannelService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Tạo bảng kênh/DM và backfill #Welcome cho workspace cũ. */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkspaceChannelsMigration {

    @PersistenceContext
    private EntityManager entityManager;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceChannelService workspaceChannelService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (!tableExists("workspace_channels")) {
                applySchema();
            }
            backfillWelcomeChannels();
        } catch (Exception e) {
            log.warn("Workspace channels migration skipped: {}", e.getMessage());
        }
    }

    private void applySchema() {
        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS workspace_channels (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
                    name VARCHAR(100) NOT NULL,
                    position INT NOT NULL DEFAULT 0,
                    is_system BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (workspace_id, name)
                )
                """).executeUpdate();

        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS channel_messages (
                    id BIGSERIAL PRIMARY KEY,
                    channel_id BIGINT NOT NULL REFERENCES workspace_channels(id) ON DELETE CASCADE,
                    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    content TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """).executeUpdate();

        entityManager.createNativeQuery("""
                CREATE INDEX IF NOT EXISTS idx_channel_messages_channel_id
                ON channel_messages(channel_id, created_at)
                """).executeUpdate();

        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS workspace_dm_conversations (
                    id BIGSERIAL PRIMARY KEY,
                    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
                    user_a_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    user_b_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (workspace_id, user_a_id, user_b_id),
                    CHECK (user_a_id < user_b_id)
                )
                """).executeUpdate();

        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS workspace_dm_messages (
                    id BIGSERIAL PRIMARY KEY,
                    conversation_id BIGINT NOT NULL REFERENCES workspace_dm_conversations(id) ON DELETE CASCADE,
                    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    content TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """).executeUpdate();

        entityManager.createNativeQuery("""
                CREATE INDEX IF NOT EXISTS idx_dm_messages_conversation_id
                ON workspace_dm_messages(conversation_id, created_at)
                """).executeUpdate();

        log.info("Created workspace channel/DM tables");
    }

    private void backfillWelcomeChannels() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        for (Workspace workspace : workspaces) {
            workspaceChannelService.ensureWelcomeChannel(workspace);
        }
        if (!workspaces.isEmpty()) {
            log.info("Ensured Welcome channel for {} workspace(s)", workspaces.size());
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
}
