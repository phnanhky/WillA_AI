package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertBookingCallTopup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpertBookingCallTopupRepository extends JpaRepository<ExpertBookingCallTopup, Long> {
    Optional<ExpertBookingCallTopup> findByPaymentId(Long paymentId);
}
