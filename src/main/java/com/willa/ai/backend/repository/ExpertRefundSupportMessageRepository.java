package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertRefundSupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpertRefundSupportMessageRepository extends JpaRepository<ExpertRefundSupportMessage, Long> {
    List<ExpertRefundSupportMessage> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    long countByBookingId(Long bookingId);
}
