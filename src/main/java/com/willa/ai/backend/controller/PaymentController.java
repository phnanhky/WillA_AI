package com.willa.ai.backend.controller;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.willa.ai.backend.dto.ApiResponse;
import com.willa.ai.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.Webhook;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin("*")
@Tag(name = "Payment Management", description = "APIs for Payment Management")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create-link")
    public ResponseEntity<ApiResponse<CheckoutResponseData>> createPaymentLink(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long planId = Long.parseLong(request.get("planId").toString());
            String email = authentication.getName(); // Từ token

            CheckoutResponseData result = paymentService.createPaymentLink(email, planId);

            return ResponseEntity.ok(ApiResponse.<CheckoutResponseData>builder()
                    .success(true)
                    .data(result)
                    .message("Payment link generated successfully")
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(ApiResponse.<CheckoutResponseData>builder()
                    .success(false)
                    .message("Failed to generate payment link: " + e.getMessage())
                    .build());
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> webhookHandler(@RequestBody Webhook webhookData) {
        try {
            paymentService.handleWebhook(webhookData);

            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .success(true)
                    .data("OK")
                    .message("Webhook processed")
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .success(false)
                    .message("Webhook error: " + e.getMessage())
                    .build());
        }
    }
}