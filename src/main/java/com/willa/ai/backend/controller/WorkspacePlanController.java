package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.WorkspacePlanRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.WorkspacePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Workspace Plans", description = "Gói workspace (tách khỏi gói feedback token)")
public class WorkspacePlanController {

    private final WorkspacePlanService workspacePlanService;

    @GetMapping("/api/v1/workspace-plans")
    @Operation(summary = "Danh sách gói workspace đang bán")
    public ResponseEntity<ApiResponse> listActive() {
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Workspace plans retrieved")
                .data(workspacePlanService.listActive())
                .build());
    }

    @GetMapping("/api/admin/workspace-plans")
    @Operation(summary = "Admin: tất cả gói workspace")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse> listAllAdmin() {
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Workspace plans fetched")
                .data(workspacePlanService.listAll())
                .build());
    }

    @PostMapping("/api/admin/workspace-plans")
    @Operation(summary = "Admin: tạo gói workspace")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse> create(@Valid @RequestBody WorkspacePlanRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace plan created")
                    .data(workspacePlanService.create(request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/api/admin/workspace-plans/{id}")
    @Operation(summary = "Admin: cập nhật gói workspace")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse> update(@PathVariable Long id, @Valid @RequestBody WorkspacePlanRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace plan updated")
                    .data(workspacePlanService.update(id, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PatchMapping("/api/admin/workspace-plans/{id}/status")
    @Operation(summary = "Admin: bật/tắt gói workspace")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse> changeStatus(@PathVariable Long id, @RequestParam boolean isActive) {
        try {
            workspacePlanService.changeStatus(id, isActive);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace plan status updated")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
