package com.willa.ai.backend.service;

import vn.payos.type.CheckoutResponseData;
import vn.payos.type.Webhook;

public interface PaymentService {
    CheckoutResponseData createPaymentLink(String userEmail, Long planId);
    void handleWebhook(Webhook webhookData);
}