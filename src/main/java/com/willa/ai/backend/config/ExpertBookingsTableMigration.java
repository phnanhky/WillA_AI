package com.willa.ai.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tạo / nâng cấp bảng expert bookings (production ddl-auto có thể là validate/none). */
@Component
@Slf4j
public class ExpertBookingsTableMigration {

    private static final String STATUS_CHECK = "expert_bookings_status_check";

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
                          meeting_room_url TEXT,
                          reject_reason TEXT,
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
                addStatusCheckConstraint();
                log.info("Created expert_bookings table");
            } else {
                upgradeExistingBookingsTable();
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

            ensureCallTrackingTables();
        } catch (Exception e) {
            log.warn("Expert bookings migration skipped: {}", e.getMessage());
        }
    }

    private void ensureCallTrackingTables() {
        if (!tableExists("expert_booking_call_events")) {
            entityManager.createNativeQuery("""
                    CREATE TABLE expert_booking_call_events (
                      id BIGSERIAL PRIMARY KEY,
                      booking_id BIGINT NOT NULL REFERENCES expert_bookings(id) ON DELETE CASCADE,
                      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                      event_type VARCHAR(100) NOT NULL,
                      room_name VARCHAR(200),
                      client_session_id VARCHAR(100),
                      payload TEXT,
                      created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE INDEX idx_expert_call_evt_booking ON expert_booking_call_events(booking_id)
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE INDEX idx_expert_call_evt_created ON expert_booking_call_events(created_at)
                    """).executeUpdate();
            log.info("Created expert_booking_call_events table");
        }
        if (!tableExists("expert_booking_call_sessions")) {
            entityManager.createNativeQuery("""
                    CREATE TABLE expert_booking_call_sessions (
                      id BIGSERIAL PRIMARY KEY,
                      booking_id BIGINT NOT NULL REFERENCES expert_bookings(id) ON DELETE CASCADE,
                      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                      room_name VARCHAR(200),
                      client_session_id VARCHAR(100),
                      joined_at TIMESTAMP,
                      left_at TIMESTAMP,
                      duration_seconds BIGINT,
                      created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE INDEX idx_expert_call_sess_booking ON expert_booking_call_sessions(booking_id)
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE INDEX idx_expert_call_sess_client ON expert_booking_call_sessions(client_session_id)
                    """).executeUpdate();
            log.info("Created expert_booking_call_sessions table");
        }
    }

    private void upgradeExistingBookingsTable() {
        if (!columnExists("expert_bookings", "drive_links")) {
            entityManager.createNativeQuery("""
                    ALTER TABLE expert_bookings ADD COLUMN drive_links TEXT
                    """).executeUpdate();
            log.info("Added expert_bookings.drive_links");
        }
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
                refreshStatusCheckConstraint();
                backfillMeetingRoomUrls();
            }

    /** Hibernate cũ chỉ cho phép PENDING_PAYMENT… — cần thêm PENDING_EXPERT, REJECTED. */
    private void refreshStatusCheckConstraint() {
        if (constraintExists("expert_bookings", STATUS_CHECK)) {
            entityManager.createNativeQuery("""
                    ALTER TABLE expert_bookings DROP CONSTRAINT expert_bookings_status_check
                    """).executeUpdate();
            log.info("Dropped legacy expert_bookings_status_check");
        }
        if (!constraintExists("expert_bookings", STATUS_CHECK)) {
            addStatusCheckConstraint();
        }
    }

    private void addStatusCheckConstraint() {
        entityManager.createNativeQuery("""
                ALTER TABLE expert_bookings ADD CONSTRAINT expert_bookings_status_check
                CHECK (status IN (
                  'PENDING_EXPERT',
                  'PENDING_PAYMENT',
                  'AWAITING_EXPERT',
                  'IN_PROGRESS',
                  'COMPLETED',
                  'REJECTED',
                  'CANCELLED'
                ))
                """).executeUpdate();
        log.info("Added expert_bookings_status_check with full status enum");
    }

    /** Booking đã thanh toán nhưng webhook chưa ghi link Jitsi. */
    private void backfillMeetingRoomUrls() {
        entityManager.createNativeQuery("""
                UPDATE expert_bookings
                SET meeting_room_url = 'https://meet.jit.si/WillaBooking' || id
                WHERE meeting_room_url IS NULL
                  AND status IN ('AWAITING_EXPERT', 'IN_PROGRESS', 'COMPLETED')
                """).executeUpdate();
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

    private boolean constraintExists(String tableName, String constraintName) {
        Object result = entityManager.createNativeQuery("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.table_constraints
                  WHERE table_schema = 'public'
                    AND table_name = :table
                    AND constraint_name = :name
                )
                """)
                .setParameter("table", tableName)
                .setParameter("name", constraintName)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
