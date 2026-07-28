package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.CreatePaymentLinkRequest;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.Webhook;

import com.willa.ai.backend.dto.response.PaymentConfirmResponse;
import com.willa.ai.backend.entity.Payment;

public interface PaymentService {
    CheckoutResponseData createPaymentLink(String userEmail, CreatePaymentLinkRequest request);
    CheckoutResponseData createCheckoutForPayment(Payment payment);
    void handleWebhook(Webhook webhookData);

    /** Xác nhận từ return URL (poll PayOS) — bù khi webhook chậm/mất. */
    PaymentConfirmResponse confirmOrderByOrderCode(Long orderCode);
}
