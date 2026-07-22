package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.WorkspaceExpertRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.ExpertBookingService;
import com.willa.ai.backend.service.ExpertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Experts", description = "Quản lý expert trong workspace")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExpertController {

    private final ExpertService expertService;
    private final ExpertBookingService expertBookingService;

    @GetMapping("/experts")
    @Operation(summary = "Danh sách tất cả expert")
    public ResponseEntity<ApiResponse> listExperts() {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Experts fetched successfully")
                    .data(expertService.listAllExperts())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/workspaces")
    @Operation(summary = "Danh sách workspace (admin)")
    public ResponseEntity<ApiResponse> listWorkspaces() {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspaces fetched successfully")
                    .data(expertService.listAllWorkspacesForAdmin())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/experts")
    @Operation(summary = "Thêm expert vào workspace")
    public ResponseEntity<ApiResponse> createExpert(@Valid @RequestBody WorkspaceExpertRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Expert added successfully")
                    .data(expertService.createExpert(request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/experts/{expertId}")
    @Operation(summary = "Cập nhật expert")
    public ResponseEntity<ApiResponse> updateExpert(
            @PathVariable Long expertId,
            @RequestBody WorkspaceExpertRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Expert updated successfully")
                    .data(expertService.updateExpert(expertId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/experts/{expertId}")
    @Operation(summary = "Xóa expert")
    public ResponseEntity<ApiResponse> deleteExpert(@PathVariable Long expertId) {
        try {
            expertService.deleteExpert(expertId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Expert removed successfully")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/expert-bookings/{bookingId}/call-history")
    @Operation(summary = "Lịch sử video call + event chi tiết (admin)")
    public ResponseEntity<ApiResponse> getCallHistory(@PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Call history retrieved")
                    .data(expertBookingService.getCallHistoryForAdmin(bookingId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/expert-call-sessions/recent")
    @Operation(summary = "Phiên gọi gần đây (admin)")
    public ResponseEntity<ApiResponse> listRecentCallSessions(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Recent call sessions retrieved")
                    .data(expertBookingService.listRecentCallSessionsForAdmin(limit))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/expert-bookings/refund-pending")
    @Operation(summary = "Đơn chờ ops hoàn tiền trên PayOS")
    public ResponseEntity<ApiResponse> listRefundPending() {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Refund-pending bookings")
                    .data(expertBookingService.listRefundPendingForAdmin())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/expert-bookings/{bookingId}/mark-refund-settled")
    @Operation(summary = "Đánh dấu đã hoàn tiền xong trên PayOS")
    public ResponseEntity<ApiResponse> markRefundSettled(@PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Refund marked settled")
                    .data(expertBookingService.markRefundSettled(bookingId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
