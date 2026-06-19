package com.willa.ai.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.willa.ai.backend.dto.request.WorkspaceProjectRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.WorkspaceProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
@Tag(name = "Workspace Project", description = "Project trong Teamspace")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceProjectController {

    private final WorkspaceProjectService workspaceProjectService;

    @GetMapping
    @Operation(summary = "Danh sách project")
    public ResponseEntity<ApiResponse> listProjects(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Projects retrieved")
                    .data(workspaceProjectService.listProjects(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping
    @Operation(summary = "Tạo project mới")
    public ResponseEntity<ApiResponse> createProject(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceProjectRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Project created")
                    .data(workspaceProjectService.createProject(auth.getName(), workspaceId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Xóa project")
    public ResponseEntity<ApiResponse> deleteProject(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long projectId) {
        try {
            workspaceProjectService.deleteProject(auth.getName(), workspaceId, projectId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Project deleted").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
