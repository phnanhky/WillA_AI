package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Page<Subscription> findByUserId(Long userId, Pageable pageable);
    List<Subscription> findSubscriptionsByStatus(SubscriptionStatus status);
    List<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan p
            WHERE s.user.id = :userId
              AND s.status = :status
              AND (s.endDate IS NULL OR s.endDate > CURRENT_TIMESTAMP)
              AND p.billingCycle IN (com.willa.ai.backend.entity.enums.BillingCycle.MONTHLY, com.willa.ai.backend.entity.enums.BillingCycle.YEARLY)
            ORDER BY s.createdAt DESC
            """)
    List<Subscription> findActiveRecurringByUserId(
            @Param("userId") Long userId,
            @Param("status") SubscriptionStatus status);
}
