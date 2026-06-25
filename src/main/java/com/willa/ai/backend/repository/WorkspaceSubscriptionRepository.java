package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.WorkspaceSubscription;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceSubscriptionRepository extends JpaRepository<WorkspaceSubscription, Long> {

    Page<WorkspaceSubscription> findByUserId(Long userId, Pageable pageable);

    List<WorkspaceSubscription> findByStatus(SubscriptionStatus status);

    @Query("""
            SELECT ws FROM WorkspaceSubscription ws
            JOIN ws.workspacePlan p
            WHERE ws.user.id = :userId
              AND ws.status = :status
              AND (ws.endDate IS NULL OR ws.endDate > CURRENT_TIMESTAMP)
              AND p.billingCycle IN (
                com.willa.ai.backend.entity.enums.BillingCycle.MONTHLY,
                com.willa.ai.backend.entity.enums.BillingCycle.YEARLY
              )
            ORDER BY ws.createdAt DESC
            """)
    List<WorkspaceSubscription> findActiveRecurringByUserId(
            @Param("userId") Long userId,
            @Param("status") SubscriptionStatus status);
}
