package com.willa.ai.backend.repository;

import com.willa.ai.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderCode(Long orderCode);
    List<Payment> findByStatusAndCreatedAtBefore(com.willa.ai.backend.entity.enums.PaymentStatus status, java.time.LocalDateTime dateTime);
}