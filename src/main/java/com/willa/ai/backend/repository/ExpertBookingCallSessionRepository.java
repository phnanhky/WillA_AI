package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertBookingCallSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpertBookingCallSessionRepository extends JpaRepository<ExpertBookingCallSession, Long> {
    List<ExpertBookingCallSession> findByBookingIdOrderByJoinedAtDesc(Long bookingId);

    Optional<ExpertBookingCallSession> findFirstByBookingIdAndClientSessionIdAndLeftAtIsNullOrderByJoinedAtDesc(
            Long bookingId, String clientSessionId);
}
