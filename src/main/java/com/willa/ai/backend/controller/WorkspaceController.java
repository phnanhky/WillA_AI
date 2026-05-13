package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspace", description = "Quản lý Không gian làm việc")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "Tạo Workspace mới")
    public ResponseEntity<ApiResponse> createWorkspace(Authentication auth, @Valid @RequestBody WorkspaceRequest request) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Workspace created successfully").data(workspaceService.createWorkspace(auth.getName(), request)).build());
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách Workspaces của user hiện tại")
    public ResponseEntity<ApiResponse> getWorkspaces(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Workspaces retrieved successfully").data(workspaceService.getUserWorkspaces(auth.getName())).build());
    }
    
    @PostMapping("/{workspaceId}/members")
    @Operation(summary = "Mời thành viên vào Workspace (Chỉ ADMIN)")
    public ResponseEntity<ApiResponse> inviteMember(Authentication auth, @PathVariable Long workspaceId, @Valid @RequestBody InviteMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Member invited successfully").data(workspaceService.inviteMember(auth.getName(), workspaceId, request)).build());
    }
    
    @GetMapping("/{workspaceId}/members")
    @Operation(summary = "Lấy danh sách thành viên trong Workspace")
    public ResponseEntity<ApiResponse> getMembers(Authentication auth, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Members retrieved successfully").data(workspaceService.getWorkspaceMembers(auth.getName(), workspaceId)).build());
    }
}
