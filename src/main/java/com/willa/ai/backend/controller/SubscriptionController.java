package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.SubscriptionRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.SubscriptionResponse;
import com.willa.ai.backend.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "AI Subscription & Buying Plans APIs")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse> subscribe(
            @Valid @RequestBody SubscriptionRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        SubscriptionResponse response = subscriptionService.subscribe(email, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Successfully subscribed to the plan")
                .data(response)
                .build());
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse> getMySubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String email = authentication.getName();
        Page<SubscriptionResponse> subscriptions = subscriptionService.getUserSubscriptions(email, page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("User subscriptions retrieved successfully")
                .data(subscriptions)
                .build());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> getAllSubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<SubscriptionResponse> subscriptions = subscriptionService.getAllSubscriptions(page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("All subscriptions retrieved successfully")
                .data(subscriptions)
                .build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse> cancelSubscription(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        SubscriptionResponse response = subscriptionService.cancelSubscription(email, id);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Subscription cancelled successfully")
                .data(response)
                .build());
    }
}
