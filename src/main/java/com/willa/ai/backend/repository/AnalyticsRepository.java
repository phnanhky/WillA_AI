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
     * Tất cả user có chat trong kỳ (sắp xếp theo số tin nhắn giảm dần).
     */
    @Query(value = """
        SELECT cs.user_id, u.email, p.name as plan_name, COUNT(cm.id) as chat_count,
               COALESCE((
                   SELECT SUM(atu.total_tokens)
                   FROM ai_token_usages atu
                   WHERE atu.user_id = cs.user_id
                     AND atu.created_at >= :startDate
                     AND atu.created_at <= :endDate
               ), 0) as tokens_used
        FROM chat_sessions cs
        LEFT JOIN chat_messages cm ON cs.id = cm.session_id
            AND cm.created_at >= :startDate
            AND cm.created_at <= :endDate
        LEFT JOIN users u ON cs.user_id = u.id
        LEFT JOIN subscriptions s ON u.id = s.user_id AND s.status = 'ACTIVE'
        LEFT JOIN plans p ON s.plan_id = p.id
        WHERE cs.created_at >= :startDate
          AND cs.created_at <= :endDate
        GROUP BY cs.user_id, u.email, p.name
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
}
