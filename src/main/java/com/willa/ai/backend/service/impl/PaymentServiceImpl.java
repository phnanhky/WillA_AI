package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.PaymentRepository;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.PaymentService;
import com.willa.ai.backend.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PayOS payOS;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Override
    @Transactional
    public CheckoutResponseData createPaymentLink(String userEmail, Long planId) {
        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

            Plan plan = planRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + planId));

            // Chuyển BigDecimal sang Long (VND)
            Long amount = plan.getPrice().longValue();
            Long orderCode = System.currentTimeMillis() / 1000;
            String description = "WillA_AI Plan " + plan.getName();

            // Lưu lịch sử thanh toán vào database với trạng thái PENDING
            Payment payment = Payment.builder()
                    .orderCode(orderCode)
                    .amount(amount)
                    .description(description)
                    .status(PaymentStatus.PENDING)
                    .user(user)
                    .plan(plan)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            paymentRepository.save(payment);

            String returnUrl = "http://localhost:3000/success?orderCode=" + orderCode;
            String cancelUrl = "http://localhost:3000/cancel?orderCode=" + orderCode;

            ItemData item = ItemData.builder()
                    .name(plan.getName())
                    .quantity(1)
                    .price(amount.intValue())
                    .build();

            List<ItemData> items = new ArrayList<>();
            items.add(item);

            PaymentData paymentData = PaymentData.builder()
                    .orderCode(orderCode)
                    .amount(amount.intValue())
                    .description(description.length() > 25 ? description.substring(0, 25) : description) // PayOS giới hạn 25 ký tự
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .item(item)
                    .build();

            return payOS.createPaymentLink(paymentData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate payment link: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleWebhook(Webhook webhookData) {
        try {
            // Xác thực WebhookData từ PayOS
            WebhookData data = payOS.verifyPaymentWebhookData(webhookData);

            if ("00".equals(data.getCode()) || "00".equals(webhookData.getCode())) {
                Long orderCode = data.getOrderCode();
                Optional<Payment> paymentOpt = paymentRepository.findByOrderCode(orderCode);

                if (paymentOpt.isPresent()) {
                    Payment payment = paymentOpt.get();

                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        payment.setStatus(PaymentStatus.PAID);
                        payment.setTransactionId(data.getReference());
                        paymentRepository.save(payment);

                        // Kích hoạt/Tạo mới subscription và nạp tokens
                        subscriptionService.createOrUpdateSubscription(payment.getUser().getEmail(), payment.getPlan().getId());
                        System.out.println("Thanh toán thành công đơn hàng: " + orderCode + ". Đã cộng token/subscription.");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Webhook processing error: " + e.getMessage());
        }
    }
}