package com.willa.ai.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tạo / nâng cấp bảng expert bookings + call tracking trước Hibernate validate
 * (prod ddl-auto=validate).
 */
@Configuration
@Slf4j
public class ExpertBookingsTableMigration {

    private static final String MIGRATION_BEAN = "expertBookingsSchemaMigration";
    private static final String STATUS_CHECK = "expert_bookings_status_check";

    @Bean(name = MIGRATION_BEAN)
    @DependsOn("workspaceExpertsSchemaMigration")
    public String migrateExpertBookingTables(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            if (!tableExists(connection, "expert_bookings")) {
                statement.execute("""
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
                          completed_at TIMESTAMP,
                          paid_at TIMESTAMP,
                          accept_deadline_at TIMESTAMP,
                          accepted_at TIMESTAMP,
                          feedback_delivered_at TIMESTAMP,
                          qa_ends_at TIMESTAMP,
                          call_minutes_limit INT
                        )
                        """);
                statement.execute("CREATE INDEX idx_expert_bookings_client ON expert_bookings(client_user_id)");
                statement.execute("CREATE INDEX idx_expert_bookings_expert ON expert_bookings(expert_id)");
                statement.execute("CREATE INDEX idx_expert_bookings_status ON expert_bookings(status)");
                addStatusCheckConstraint(statement);
                log.info("Created expert_bookings table");
            } else {
                upgradeExistingBookingsTable(connection, statement);
            }

            if (!tableExists(connection, "expert_booking_messages")) {
                statement.execute("""
                        CREATE TABLE expert_booking_messages (
                          id BIGSERIAL PRIMARY KEY,
                          booking_id BIGINT NOT NULL REFERENCES expert_bookings(id) ON DELETE CASCADE,
                          sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          content TEXT NOT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("CREATE INDEX idx_expert_booking_msg_booking ON expert_booking_messages(booking_id)");
                log.info("Created expert_booking_messages table");
            }

            if (!tableExists(connection, "expert_booking_attachments")) {
                statement.execute("""
                        CREATE TABLE expert_booking_attachments (
                          id BIGSERIAL PRIMARY KEY,
                          booking_id BIGINT NOT NULL REFERENCES expert_bookings(id) ON DELETE CASCADE,
                          file_name VARCHAR(500) NOT NULL,
                          file_url TEXT NOT NULL,
                          file_size_bytes BIGINT,
                          content_type VARCHAR(200),
                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
                        )
                        """);
                statement.execute("CREATE INDEX idx_expert_booking_att_booking ON expert_booking_attachments(booking_id)");
                log.info("Created expert_booking_attachments table");
            }

            ensureCallTrackingTables(connection, statement);
            return "migrated";
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate expert booking tables", e);
        }
    }

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnExpertBookingsMigration() {
        return ExpertBookingsTableMigration::ensureEntityManagerDependsOnMigration;
    }

    private static void ensureEntityManagerDependsOnMigration(ConfigurableListableBeanFactory beanFactory) {
        if (!beanFactory.containsBeanDefinition("entityManagerFactory")) {
            return;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition("entityManagerFactory");
        String[] dependsOn = definition.getDependsOn();
        if (dependsOn != null) {
            for (String name : dependsOn) {
                if (MIGRATION_BEAN.equals(name)) {
                    return;
                }
            }
        }
        if (dependsOn == null || dependsOn.length == 0) {
            definition.setDependsOn(MIGRATION_BEAN);
            return;
        }
        String[] next = new String[dependsOn.length + 1];
        System.arraycopy(dependsOn, 0, next, 0, dependsOn.length);
        next[dependsOn.length] = MIGRATION_BEAN;
        definition.setDependsOn(next);
    }

    private static void ensureCallTrackingTables(Connection connection, Statement statement) throws SQLException {
        if (!tableExists(connection, "expert_booking_call_events")) {
            statement.execute("""
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
                    """);
            statement.execute("CREATE INDEX idx_expert_call_evt_booking ON expert_booking_call_events(booking_id)");
            statement.execute("CREATE INDEX idx_expert_call_evt_created ON expert_booking_call_events(created_at)");
            log.info("Created expert_booking_call_events table");
        }
        if (!tableExists(connection, "expert_booking_call_sessions")) {
            statement.execute("""
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
                    """);
            statement.execute("CREATE INDEX idx_expert_call_sess_booking ON expert_booking_call_sessions(booking_id)");
            statement.execute("CREATE INDEX idx_expert_call_sess_client ON expert_booking_call_sessions(client_session_id)");
            log.info("Created expert_booking_call_sessions table");
        }
    }

    private static void upgradeExistingBookingsTable(Connection connection, Statement statement) throws SQLException {
        if (!columnExists(connection, "expert_bookings", "drive_links")) {
            statement.execute("ALTER TABLE expert_bookings ADD COLUMN drive_links TEXT");
            log.info("Added expert_bookings.drive_links");
        }
        if (!columnExists(connection, "expert_bookings", "meeting_room_url")) {
            statement.execute("ALTER TABLE expert_bookings ADD COLUMN meeting_room_url TEXT");
            log.info("Added expert_bookings.meeting_room_url");
        }
        if (!columnExists(connection, "expert_bookings", "reject_reason")) {
            statement.execute("ALTER TABLE expert_bookings ADD COLUMN reject_reason TEXT");
            log.info("Added expert_bookings.reject_reason");
        }
        addColumnIfMissing(connection, statement, "paid_at", "TIMESTAMP");
        addColumnIfMissing(connection, statement, "accept_deadline_at", "TIMESTAMP");
        addColumnIfMissing(connection, statement, "accepted_at", "TIMESTAMP");
        addColumnIfMissing(connection, statement, "feedback_delivered_at", "TIMESTAMP");
        addColumnIfMissing(connection, statement, "qa_ends_at", "TIMESTAMP");
        addColumnIfMissing(connection, statement, "call_minutes_limit", "INT");
        // Backfill SLA for đơn đang chờ Accept
        statement.execute("""
                UPDATE expert_bookings
                SET paid_at = COALESCE(paid_at, updated_at, created_at),
                    accept_deadline_at = COALESCE(
                        accept_deadline_at,
                        COALESCE(paid_at, updated_at, created_at) + INTERVAL '24 hours'
                    ),
                    call_minutes_limit = COALESCE(
                        call_minutes_limit,
                        CASE
                          WHEN booking_type = 'HOURLY' THEN GREATEST(COALESCE(hourly_hours, 1), 1) * 60
                          ELSE 15
                        END
                    )
                WHERE status = 'AWAITING_EXPERT'
                """);
        refreshStatusCheckConstraint(connection, statement);
        statement.execute("""
                UPDATE expert_bookings
                SET meeting_room_url = 'https://meet.jit.si/WillaBooking' || id
                WHERE meeting_room_url IS NULL
                  AND status IN ('AWAITING_EXPERT', 'IN_PROGRESS', 'COMPLETED')
                """);
    }

    private static void addColumnIfMissing(
            Connection connection, Statement statement, String column, String sqlType) throws SQLException {
        if (!columnExists(connection, "expert_bookings", column)) {
            statement.execute("ALTER TABLE expert_bookings ADD COLUMN " + column + " " + sqlType);
            log.info("Added expert_bookings.{}", column);
        }
    }

    private static void refreshStatusCheckConstraint(Connection connection, Statement statement) throws SQLException {
        if (constraintExists(connection, "expert_bookings", STATUS_CHECK)) {
            statement.execute("ALTER TABLE expert_bookings DROP CONSTRAINT expert_bookings_status_check");
            log.info("Dropped legacy expert_bookings_status_check");
        }
        if (!constraintExists(connection, "expert_bookings", STATUS_CHECK)) {
            addStatusCheckConstraint(statement);
        }
    }

    private static void addStatusCheckConstraint(Statement statement) throws SQLException {
        statement.execute("""
                ALTER TABLE expert_bookings ADD CONSTRAINT expert_bookings_status_check
                CHECK (status IN (
                  'PENDING_EXPERT',
                  'PENDING_PAYMENT',
                  'AWAITING_EXPERT',
                  'IN_PROGRESS',
                  'COMPLETED',
                  'REJECTED',
                  'CANCELLED',
                  'EXPIRED'
                ))
                """);
        log.info("Added expert_bookings_status_check with full status enum");
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = ?
                )
                """)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'public'
                    AND table_name = ?
                    AND column_name = ?
                )
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean constraintExists(Connection connection, String tableName, String constraintName)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.table_constraints
                  WHERE table_schema = 'public'
                    AND table_name = ?
                    AND constraint_name = ?
                )
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
