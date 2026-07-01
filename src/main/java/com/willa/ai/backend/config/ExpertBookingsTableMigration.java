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
public class ExpertBookingsTableMigration {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            if (!tableExists("expert_bookings")) {
                entityManager.createNativeQuery("""
                        CREATE TABLE expert_bookings (
                          id BIGSERIAL PRIMARY KEY,
                          client_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          expert_id BIGINT NOT NULL REFERENCES workspace_experts(id) ON DELETE CASCADE,
                          booking_type VARCHAR(20) NOT NULL,
                          status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
                          brief TEXT,
                          publications TEXT,
                          drive_links TEXT,
                          expert_feedback TEXT,
                          hourly_hours INT,
                          amount_vnd BIGINT NOT NULL,
                          parent_booking_id BIGINT REFERENCES expert_bookings(id) ON DELETE SET NULL,
                          payment_id BIGINT REFERENCES payments(id) ON DELETE SET NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          completed_at TIMESTAMP
                        )
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_expert_bookings_client ON expert_bookings(client_user_id)
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_expert_bookings_expert ON expert_bookings(expert_id)
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_expert_bookings_status ON expert_bookings(status)
                        """).executeUpdate();
                log.info("Created expert_bookings table");
            } else if (!columnExists("expert_bookings", "drive_links")) {
                entityManager.createNativeQuery("""
                        ALTER TABLE expert_bookings ADD COLUMN drive_links TEXT
                        """).executeUpdate();
                log.info("Added expert_bookings.drive_links");
            }

            if (tableExists("expert_bookings")) {
                if (!columnExists("expert_bookings", "meeting_room_url")) {
                    entityManager.createNativeQuery("""
                            ALTER TABLE expert_bookings ADD COLUMN meeting_room_url TEXT
                            """).executeUpdate();
                    log.info("Added expert_bookings.meeting_room_url");
                }
                if (!columnExists("expert_bookings", "reject_reason")) {
                    entityManager.createNativeQuery("""
                            ALTER TABLE expert_bookings ADD COLUMN reject_reason TEXT
                            """).executeUpdate();
                    log.info("Added expert_bookings.reject_reason");
                }
            }

            if (!tableExists("expert_booking_messages")) {
                entityManager.createNativeQuery("""
                        CREATE TABLE expert_booking_messages (
                          id BIGSERIAL PRIMARY KEY,
                          booking_id BIGINT NOT NULL REFERENCES expert_bookings(id) ON DELETE CASCADE,
                          sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          content TEXT NOT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_expert_booking_msg_booking ON expert_booking_messages(booking_id)
                        """).executeUpdate();
                log.info("Created expert_booking_messages table");
            }

            if (!tableExists("expert_booking_attachments")) {
                entityManager.createNativeQuery("""
                        CREATE TABLE expert_booking_attachments (
                          id BIGSERIAL PRIMARY KEY,
                          booking_id BIGINT NOT NULL REFERENCES expert_bookings(id) ON DELETE CASCADE,
                          file_name VARCHAR(500) NOT NULL,
                          file_url TEXT NOT NULL,
                          file_size_bytes BIGINT,
                          content_type VARCHAR(200),
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        CREATE INDEX idx_expert_booking_att_booking ON expert_booking_attachments(booking_id)
                        """).executeUpdate();
                log.info("Created expert_booking_attachments table");
            }
        } catch (Exception e) {
            log.warn("Expert bookings migration skipped: {}", e.getMessage());
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
