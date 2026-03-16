package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.Subscription;
import com.willa.ai.backend.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @EntityGraph(attributePaths = {"user", "plan"})
    Page<Subscription> findByUserId(Long userId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "plan"})
    @Override
    Page<Subscription> findAll(Pageable pageable);

    List<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);
}
