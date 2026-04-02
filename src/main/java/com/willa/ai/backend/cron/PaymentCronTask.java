package com.willa.ai.backend.cron;

import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCronTask {

    private final PaymentRepository paymentRepository;

    /**
     * Chạy mỗi giờ. Tìm các đơn hàng PENDING cũ hơn 24 giờ và cập nhật thành CANCELLED.
     */
    @Scheduled(cron = "0 0 * * * *") // Chạy vào phút 0 của mỗi giờ
    @Transactional
    public void cancelUnpaidOrders() {
        log.info("Bắt đầu quét và huỷ các đơn hàng chưa thanh toán...");

        // Tìm các order PENDING cách đây hơn 24 giờ
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Payment> stalePayments = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff);

        if (!stalePayments.isEmpty()) {
            stalePayments.forEach(payment -> {
                payment.setStatus(PaymentStatus.CANCELLED);
            });
            paymentRepository.saveAll(stalePayments);
            log.info("Đã huỷ {} đơn hàng chưa thanh toán (tạo trước {}).", stalePayments.size(), cutoff);
        } else {
            log.info("Không có đơn hàng quá hạn chưa thanh toán nào.");
        }
    }
}
