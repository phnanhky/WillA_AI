package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertBookingCallEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpertBookingCallEventRepository extends JpaRepository<ExpertBookingCallEvent, Long> {
    List<ExpertBookingCallEvent> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
