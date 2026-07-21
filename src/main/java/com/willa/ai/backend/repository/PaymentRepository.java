package com.willa.ai.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.enums.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderCode(Long orderCode);
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, java.time.LocalDateTime dateTime);
    boolean existsByCoupon_IdAndStatus(Long couponId, PaymentStatus status);
    long countByCoupon_IdAndStatus(Long couponId, PaymentStatus status);
    boolean existsByCoupon_IdAndUser_IdAndStatus(Long couponId, Long userId, PaymentStatus status);
}