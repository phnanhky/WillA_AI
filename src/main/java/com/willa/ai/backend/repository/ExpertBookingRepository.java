package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.ExpertBooking;
import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import com.willa.ai.backend.entity.enums.ExpertBookingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpertBookingRepository extends JpaRepository<ExpertBooking, Long> {

    List<ExpertBooking> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<ExpertBooking> findByExpertUserIdOrderByCreatedAtDesc(Long expertUserId);

    Optional<ExpertBooking> findByPaymentId(Long paymentId);

    List<ExpertBooking> findByStatusAndAcceptDeadlineAtBefore(
            ExpertBookingStatus status, LocalDateTime deadline);

    List<ExpertBooking> findByStatusAndBookingTypeAndQaEndsAtBefore(
            ExpertBookingStatus status,
            ExpertBookingType bookingType,
            LocalDateTime qaEndsAt);
}
