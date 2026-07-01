package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertBookingMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpertBookingMessageRepository extends JpaRepository<ExpertBookingMessage, Long> {

    List<ExpertBookingMessage> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
