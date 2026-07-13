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
}
