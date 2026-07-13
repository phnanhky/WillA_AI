package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsRepository extends JpaRepository<ChatMessage, Long> {
    
    /**
     * Lấy số chat messages mỗi ngày trong khoảng thời gian
     */
    @Query(value = """
        SELECT DATE(cm.created_at) as date, COUNT(*) as count
        FROM chat_messages cm
        WHERE cm.created_at >= :startDate
        AND cm.created_at <= :endDate
        GROUP BY DATE(cm.created_at)
        ORDER BY date ASC
        """, nativeQuery = true)
    List<Object[]> getChatCountByDate(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * User có chat trong kỳ + gói Feedback cao nhất trong kỳ (Pro > Student > Free).
     * Không có subscription Feedback overlapping kỳ → Free.
     */
    @Query(value = """
        SELECT cs.user_id,
               u.email,
               COALESCE((
                   SELECT CASE
                       WHEN MAX(CASE
                           WHEN LOWER(pl.name) LIKE '%pro%' THEN 3
                           WHEN LOWER(pl.name) LIKE '%student%' THEN 2
                           WHEN LOWER(pl.name) LIKE '%free%' THEN 1
                           ELSE 0
                       END) = 3 THEN 'Pro'
                       WHEN MAX(CASE
                           WHEN LOWER(pl.name) LIKE '%pro%' THEN 3
                           WHEN LOWER(pl.name) LIKE '%student%' THEN 2
                           WHEN LOWER(pl.name) LIKE '%free%' THEN 1
                           ELSE 0
                       END) = 2 THEN 'Student'
                       ELSE 'Free'
                   END
                   FROM subscriptions sub
                   JOIN plans pl ON pl.id = sub.plan_id
                   WHERE sub.user_id = cs.user_id
                     AND pl.billing_cycle IN ('MONTHLY', 'YEARLY')
                     AND sub.start_date <= :endDate
                     AND sub.end_date >= :startDate
               ), 'Free') AS plan_name,
               COUNT(cm.id) AS chat_count,
               COALESCE((
                   SELECT SUM(atu.total_tokens)
                   FROM ai_token_usages atu
                   WHERE atu.user_id = cs.user_id
                     AND atu.created_at >= :startDate
                     AND atu.created_at <= :endDate
               ), 0) AS tokens_used
        FROM chat_sessions cs
        LEFT JOIN chat_messages cm ON cs.id = cm.session_id
            AND cm.created_at >= :startDate
            AND cm.created_at <= :endDate
        LEFT JOIN users u ON cs.user_id = u.id
        WHERE cs.created_at >= :startDate
          AND cs.created_at <= :endDate
        GROUP BY cs.user_id, u.email
        HAVING COUNT(cm.id) > 0
        ORDER BY chat_count DESC
        """, nativeQuery = true)
    List<Object[]> getActiveUsersInPeriod(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Lấy số users active từ một ngày
     */
    @Query(value = """
        SELECT COUNT(DISTINCT cs.user_id)
        FROM chat_sessions cs
        WHERE cs.created_at >= :startDate
          AND cs.created_at <= :endDate
        """, nativeQuery = true)
    Long getActiveUserCount(@Param("startDate") LocalDateTime startDate,
                            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Lấy số chat trong khoảng thời gian
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM chat_messages cm
        WHERE cm.created_at >= :startDate
        AND cm.created_at <= :endDate
        """, nativeQuery = true)
    Long getChatCount(@Param("startDate") LocalDateTime startDate,
                      @Param("endDate") LocalDateTime endDate);
    
    /**
     * Lấy feature usage statistics
     */
    @Query(value = """
        SELECT atu.service_type, COUNT(*) as usage_count
        FROM ai_token_usages atu
        WHERE atu.created_at >= :startDate
          AND atu.created_at <= :endDate
          AND atu.service_type IS NOT NULL
        GROUP BY atu.service_type
        ORDER BY usage_count DESC
        """, nativeQuery = true)
    List<Object[]> getFeatureUsageStats(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Lấy số users và chats theo plan
     */
    @Query(value = """
        SELECT COALESCE(p.name, 'Unknown'), COUNT(DISTINCT u.id) as user_count
        FROM users u
        LEFT JOIN subscriptions s ON u.id = s.user_id AND s.status = 'ACTIVE'
        LEFT JOIN plans p ON s.plan_id = p.id
        GROUP BY COALESCE(p.name, 'Unknown')
        """, nativeQuery = true)
    List<Object[]> getUsersByPlan();
    
    @Query(value = """
        SELECT COALESCE(p.name, 'Unknown'), COUNT(*)
        FROM chat_messages cm
        JOIN chat_sessions cs ON cm.session_id = cs.id
        LEFT JOIN subscriptions s ON cs.user_id = s.user_id AND s.status = 'ACTIVE'
        LEFT JOIN plans p ON s.plan_id = p.id
        WHERE cm.created_at >= :startDate
          AND cm.created_at <= :endDate
        GROUP BY COALESCE(p.name, 'Unknown')
        """, nativeQuery = true)
    List<Object[]> getChatsByPlan(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    /** Số tài khoản đăng ký mới trong kỳ. */
    @Query(value = """
        SELECT COUNT(*)
        FROM users u
        WHERE u.created_at >= :startDate
          AND u.created_at <= :endDate
        """, nativeQuery = true)
    Long countNewRegistrations(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Số user bắt đầu gói Feedback (MONTHLY/YEARLY) trong kỳ — chuẩn hóa Free/Student/Pro.
     * Mỗi user chỉ đếm 1 lần / tier (nếu mua nhiều lần cùng tier).
     */
    @Query(value = """
        SELECT CASE
                   WHEN LOWER(p.name) LIKE '%pro%' THEN 'Pro'
                   WHEN LOWER(p.name) LIKE '%student%' THEN 'Student'
                   ELSE 'Free'
               END AS plan_tier,
               COUNT(DISTINCT s.user_id)
        FROM subscriptions s
        JOIN plans p ON p.id = s.plan_id
        WHERE s.start_date >= :startDate
          AND s.start_date <= :endDate
          AND p.billing_cycle IN ('MONTHLY', 'YEARLY')
        GROUP BY CASE
                   WHEN LOWER(p.name) LIKE '%pro%' THEN 'Pro'
                   WHEN LOWER(p.name) LIKE '%student%' THEN 'Student'
                   ELSE 'Free'
                 END
        """, nativeQuery = true)
    List<Object[]> countFeedbackPlanStartsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Gói Feedback cao nhất của mỗi user trong kỳ (Pro > Student > Free).
     * Không có sub overlapping → không trả row (caller mặc định Free).
     */
    @Query(value = """
        SELECT s.user_id,
               CASE
                   WHEN MAX(CASE
                       WHEN LOWER(p.name) LIKE '%pro%' THEN 3
                       WHEN LOWER(p.name) LIKE '%student%' THEN 2
                       WHEN LOWER(p.name) LIKE '%free%' THEN 1
                       ELSE 0
                   END) = 3 THEN 'Pro'
                   WHEN MAX(CASE
                       WHEN LOWER(p.name) LIKE '%pro%' THEN 3
                       WHEN LOWER(p.name) LIKE '%student%' THEN 2
                       WHEN LOWER(p.name) LIKE '%free%' THEN 1
                       ELSE 0
                   END) = 2 THEN 'Student'
                   ELSE 'Free'
               END AS plan_tier
        FROM subscriptions s
        JOIN plans p ON p.id = s.plan_id
        WHERE p.billing_cycle IN ('MONTHLY', 'YEARLY')
          AND s.start_date <= :endDate
          AND s.end_date >= :startDate
        GROUP BY s.user_id
        """, nativeQuery = true)
    List<Object[]> getHighestFeedbackPlanByUserInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** Tổng token AI trong kỳ. */
    @Query(value = """
        SELECT COALESCE(SUM(atu.total_tokens), 0)
        FROM ai_token_usages atu
        WHERE atu.created_at >= :startDate
          AND atu.created_at <= :endDate
        """, nativeQuery = true)
    Long sumTokensInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** Token AI theo user trong kỳ. */
    @Query(value = """
        SELECT atu.user_id, COALESCE(SUM(atu.total_tokens), 0)
        FROM ai_token_usages atu
        WHERE atu.created_at >= :startDate
          AND atu.created_at <= :endDate
        GROUP BY atu.user_id
        """, nativeQuery = true)
    List<Object[]> sumTokensByUserInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // ── Expert analytics ──────────────────────────────────────────────

    @Query(value = """
        SELECT COUNT(*)
        FROM expert_bookings eb
        WHERE eb.created_at >= :startDate
          AND eb.created_at <= :endDate
        """, nativeQuery = true)
    Long countExpertBookingsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*)
        FROM expert_bookings eb
        WHERE eb.status = 'COMPLETED'
          AND COALESCE(eb.completed_at, eb.updated_at) >= :startDate
          AND COALESCE(eb.completed_at, eb.updated_at) <= :endDate
        """, nativeQuery = true)
    Long countExpertCompletedInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COALESCE(SUM(eb.amount_vnd), 0)
        FROM expert_bookings eb
        WHERE eb.created_at >= :startDate
          AND eb.created_at <= :endDate
          AND eb.status IN ('AWAITING_EXPERT', 'IN_PROGRESS', 'COMPLETED')
        """, nativeQuery = true)
    Long sumExpertPaidRevenueInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(DISTINCT eb.client_user_id)
        FROM expert_bookings eb
        WHERE eb.created_at >= :startDate
          AND eb.created_at <= :endDate
        """, nativeQuery = true)
    Long countExpertUniqueClientsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(DISTINCT eb.expert_id)
        FROM expert_bookings eb
        WHERE eb.created_at >= :startDate
          AND eb.created_at <= :endDate
        """, nativeQuery = true)
    Long countExpertUniqueBookedInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*)
        FROM workspace_experts we
        WHERE we.is_active = true
        """, nativeQuery = true)
    Long countActiveExperts();

    @Query(value = """
        SELECT eb.status, COUNT(*)
        FROM expert_bookings eb
        WHERE eb.created_at >= :startDate
          AND eb.created_at <= :endDate
        GROUP BY eb.status
        """, nativeQuery = true)
    List<Object[]> countExpertBookingsByStatus(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT eb.booking_type, COUNT(*)
        FROM expert_bookings eb
        WHERE eb.created_at >= :startDate
          AND eb.created_at <= :endDate
        GROUP BY eb.booking_type
        """, nativeQuery = true)
    List<Object[]> countExpertBookingsByType(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*)
        FROM expert_booking_messages m
        WHERE m.created_at >= :startDate
          AND m.created_at <= :endDate
        """, nativeQuery = true)
    Long countExpertMessagesInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*), COALESCE(SUM(s.duration_seconds), 0)
        FROM expert_booking_call_sessions s
        WHERE s.created_at >= :startDate
          AND s.created_at <= :endDate
        """, nativeQuery = true)
    List<Object[]> expertCallSessionStatsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COALESCE(SUM(eb.amount_vnd), 0)
        FROM expert_bookings eb
        WHERE eb.status = 'COMPLETED'
          AND COALESCE(eb.completed_at, eb.updated_at) >= :startDate
          AND COALESCE(eb.completed_at, eb.updated_at) <= :endDate
        """, nativeQuery = true)
    Long sumExpertPayableGrossInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COALESCE(SUM(CASE
                   WHEN eb.booking_type = 'HOURLY' THEN COALESCE(eb.hourly_hours, 0)
                   ELSE 0 END), 0)
        FROM expert_bookings eb
        WHERE eb.status = 'COMPLETED'
          AND COALESCE(eb.completed_at, eb.updated_at) >= :startDate
          AND COALESCE(eb.completed_at, eb.updated_at) <= :endDate
        """, nativeQuery = true)
    Long sumExpertPayableHourlyHoursInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT we.id,
               COALESCE(u.full_name, u.email) AS expert_name,
               u.email,
               COUNT(*) AS completed_count,
               COALESCE(SUM(CASE WHEN eb.booking_type = 'REVIEW' THEN 1 ELSE 0 END), 0) AS review_count,
               COALESCE(SUM(CASE WHEN eb.booking_type = 'REVIEW' THEN eb.amount_vnd ELSE 0 END), 0) AS review_gross,
               COALESCE(SUM(CASE WHEN eb.booking_type = 'HOURLY' THEN 1 ELSE 0 END), 0) AS hourly_count,
               COALESCE(SUM(CASE
                   WHEN eb.booking_type = 'HOURLY' THEN COALESCE(eb.hourly_hours, 0)
                   ELSE 0 END), 0) AS hourly_hours,
               COALESCE(SUM(CASE WHEN eb.booking_type = 'HOURLY' THEN eb.amount_vnd ELSE 0 END), 0) AS hourly_gross,
               COALESCE(SUM(eb.amount_vnd), 0) AS payable_gross,
               COALESCE((
                   SELECT SUM(s.duration_seconds)
                   FROM expert_booking_call_sessions s
                   JOIN expert_bookings eb2 ON eb2.id = s.booking_id
                   WHERE eb2.expert_id = we.id
                     AND eb2.status = 'COMPLETED'
                     AND COALESCE(eb2.completed_at, eb2.updated_at) >= :startDate
                     AND COALESCE(eb2.completed_at, eb2.updated_at) <= :endDate
               ), 0) AS call_seconds
        FROM expert_bookings eb
        JOIN workspace_experts we ON we.id = eb.expert_id
        JOIN users u ON u.id = we.user_id
        WHERE eb.status = 'COMPLETED'
          AND COALESCE(eb.completed_at, eb.updated_at) >= :startDate
          AND COALESCE(eb.completed_at, eb.updated_at) <= :endDate
        GROUP BY we.id, u.full_name, u.email
        ORDER BY payable_gross DESC
        """, nativeQuery = true)
    List<Object[]> expertPayrollInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT eb.id,
               we.id,
               COALESCE(u.full_name, u.email),
               u.email,
               cu.email,
               eb.booking_type,
               eb.hourly_hours,
               eb.amount_vnd,
               COALESCE(eb.completed_at, eb.updated_at)
        FROM expert_bookings eb
        JOIN workspace_experts we ON we.id = eb.expert_id
        JOIN users u ON u.id = we.user_id
        JOIN users cu ON cu.id = eb.client_user_id
        WHERE eb.status = 'COMPLETED'
          AND COALESCE(eb.completed_at, eb.updated_at) >= :startDate
          AND COALESCE(eb.completed_at, eb.updated_at) <= :endDate
        ORDER BY COALESCE(eb.completed_at, eb.updated_at) DESC, eb.id DESC
        LIMIT 500
        """, nativeQuery = true)
    List<Object[]> expertCompletedJobsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT we.id,
               COALESCE(u.full_name, u.email) AS expert_name,
               u.email,
               COUNT(*) AS booking_count,
               COALESCE(SUM(CASE
                   WHEN eb.status IN ('AWAITING_EXPERT', 'IN_PROGRESS', 'COMPLETED')
                   THEN eb.amount_vnd ELSE 0 END), 0) AS paid_revenue,
               COALESCE(SUM(CASE WHEN eb.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_count
        FROM expert_bookings eb
        JOIN workspace_experts we ON we.id = eb.expert_id
        JOIN users u ON u.id = we.user_id
        WHERE eb.created_at >= :startDate
          AND eb.created_at <= :endDate
        GROUP BY we.id, u.full_name, u.email
        ORDER BY booking_count DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Object[]> topExpertsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // ── Workspace analytics ───────────────────────────────────────────

    @Query(value = """
        SELECT COUNT(*)
        FROM workspaces w
        WHERE w.created_at >= :startDate
          AND w.created_at <= :endDate
        """, nativeQuery = true)
    Long countWorkspacesCreatedInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*) FROM workspaces
        """, nativeQuery = true)
    Long countTotalWorkspaces();

    @Query(value = """
        SELECT COUNT(*)
        FROM workspace_members wm
        WHERE wm.joined_at >= :startDate
          AND wm.joined_at <= :endDate
        """, nativeQuery = true)
    Long countMembersJoinedInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*) FROM workspace_members
        """, nativeQuery = true)
    Long countTotalWorkspaceMembers();

    @Query(value = """
        SELECT COUNT(*)
        FROM channel_messages cm
        WHERE cm.created_at >= :startDate
          AND cm.created_at <= :endDate
        """, nativeQuery = true)
    Long countChannelMessagesInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*)
        FROM workspace_dm_messages dm
        WHERE dm.created_at >= :startDate
          AND dm.created_at <= :endDate
        """, nativeQuery = true)
    Long countDmMessagesInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*)
        FROM workspace_subscriptions ws
        WHERE ws.status = 'ACTIVE'
          AND ws.end_date >= NOW()
        """, nativeQuery = true)
    Long countActiveWorkspaceSubscriptions();

    @Query(value = """
        SELECT COUNT(DISTINCT ws.user_id)
        FROM workspace_subscriptions ws
        WHERE ws.start_date >= :startDate
          AND ws.start_date <= :endDate
        """, nativeQuery = true)
    Long countWorkspacePlanStartsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT COUNT(*)
        FROM workspace_projects wp
        WHERE wp.created_at >= :startDate
          AND wp.created_at <= :endDate
        """, nativeQuery = true)
    Long countProjectsCreatedInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT w.id,
               w.title,
               u.email,
               (SELECT COUNT(*) FROM workspace_members wm WHERE wm.workspace_id = w.id) AS member_count,
               COALESCE((
                   SELECT COUNT(*)
                   FROM channel_messages cm
                   JOIN workspace_channels wc ON wc.id = cm.channel_id
                   WHERE wc.workspace_id = w.id
                     AND cm.created_at >= :startDate
                     AND cm.created_at <= :endDate
               ), 0) AS msg_count
        FROM workspaces w
        JOIN users u ON u.id = w.owner_id
        WHERE (w.created_at >= :startDate AND w.created_at <= :endDate)
           OR EXISTS (
               SELECT 1 FROM channel_messages cm2
               JOIN workspace_channels wc2 ON wc2.id = cm2.channel_id
               WHERE wc2.workspace_id = w.id
                 AND cm2.created_at >= :startDate
                 AND cm2.created_at <= :endDate
           )
        ORDER BY msg_count DESC, member_count DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Object[]> topWorkspacesInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
