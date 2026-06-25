package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.WorkspaceSubscriptionResponse;
import com.willa.ai.backend.service.WorkspaceSubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspace-subscriptions")
@RequiredArgsConstructor
@Tag(name = "Workspace Subscription", description = "Workspace plan subscription APIs")
public class WorkspaceSubscriptionController {

    private final WorkspaceSubscriptionService workspaceSubscriptionService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse> getMySubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String email = authentication.getName();
        Page<WorkspaceSubscriptionResponse> subscriptions =
                workspaceSubscriptionService.getUserSubscriptions(email, page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Workspace subscriptions retrieved successfully")
                .data(subscriptions)
                .build());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> getAllSubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<WorkspaceSubscriptionResponse> subscriptions =
                workspaceSubscriptionService.getAllSubscriptions(page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("All workspace subscriptions retrieved successfully")
                .data(subscriptions)
                .build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse> cancelSubscription(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        WorkspaceSubscriptionResponse response =
                workspaceSubscriptionService.cancelSubscription(email, id);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Workspace subscription cancelled successfully")
                .data(response)
                .build());
    }
}
