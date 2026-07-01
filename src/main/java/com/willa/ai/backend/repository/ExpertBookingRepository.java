package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertBooking;
import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpertBookingRepository extends JpaRepository<ExpertBooking, Long> {

    List<ExpertBooking> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<ExpertBooking> findByExpertUserIdOrderByCreatedAtDesc(Long expertUserId);

    java.util.Optional<ExpertBooking> findByPaymentId(Long paymentId);
}
