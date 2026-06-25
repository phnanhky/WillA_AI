package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.UserResponse;
import com.willa.ai.backend.entity.enums.WorkspacePlanTier;
import com.willa.ai.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "Quản trị người dùng (gói workspace)")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;

    @PatchMapping("/{userId}/workspace-plan")
    @Operation(summary = "Cập nhật gói workspace cho user (theo plan id)")
    public ResponseEntity<ApiResponse> updateWorkspacePlanById(
            @PathVariable Long userId,
            @RequestParam Long workspacePlanId) {
        try {
            UserResponse user = userService.updateUserWorkspacePlan(userId, workspacePlanId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace plan updated")
                    .data(user)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PatchMapping("/{userId}/workspace-plan-tier")
    @Operation(summary = "Cập nhật gói workspace theo mã tier (legacy)")
    public ResponseEntity<ApiResponse> updateWorkspacePlanByTier(
            @PathVariable Long userId,
            @RequestParam WorkspacePlanTier tier) {
        try {
            UserResponse user = userService.updateWorkspacePlanTier(userId, tier);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace plan updated")
                    .data(user)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
