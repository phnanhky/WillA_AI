package com.willa.ai.backend.service;

import vn.payos.type.CheckoutResponseData;
import vn.payos.type.Webhook;

import com.willa.ai.backend.dto.response.PaymentConfirmResponse;
import com.willa.ai.backend.entity.Payment;

public interface PaymentService {
    CheckoutResponseData createPaymentLink(String userEmail, Long planId, String planType, String couponCode);
    CheckoutResponseData createCheckoutForPayment(Payment payment);
    void handleWebhook(Webhook webhookData);

    /** Xác nhận từ return URL (poll PayOS) — bù khi webhook chậm/mất. */
    PaymentConfirmResponse confirmOrderByOrderCode(Long orderCode);
}
