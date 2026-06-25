package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.WorkspaceExpertRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.ExpertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Experts", description = "Quản lý expert trong workspace")
@SecurityRequirement(name = "bearerAuth")
public class AdminExpertController {

    private final ExpertService expertService;

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
}
