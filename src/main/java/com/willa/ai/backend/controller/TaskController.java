package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.TaskCommentRequest;
import com.willa.ai.backend.dto.request.TaskRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/tasks")
@RequiredArgsConstructor
@Tag(name = "Task", description = "Quản lý task trong workspace")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {
    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "Danh sách task")
    public ResponseEntity<ApiResponse> listTasks(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Tasks retrieved")
                    .data(taskService.listTasks(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Chi tiết task")
    public ResponseEntity<ApiResponse> getTask(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Task retrieved")
                    .data(taskService.getTask(auth.getName(), workspaceId, taskId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping
    @Operation(summary = "Tạo task mới")
    public ResponseEntity<ApiResponse> createTask(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody TaskRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Task created")
                    .data(taskService.createTask(auth.getName(), workspaceId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Cập nhật task")
    public ResponseEntity<ApiResponse> updateTask(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Task updated")
                    .data(taskService.updateTask(auth.getName(), workspaceId, taskId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "Xóa task")
    public ResponseEntity<ApiResponse> deleteTask(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId) {
        try {
            taskService.deleteTask(auth.getName(), workspaceId, taskId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Task deleted").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{taskId}/comments")
    @Operation(summary = "Bình luận của task")
    public ResponseEntity<ApiResponse> listComments(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Comments retrieved")
                    .data(taskService.listComments(auth.getName(), workspaceId, taskId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{taskId}/comments")
    @Operation(summary = "Thêm bình luận")
    public ResponseEntity<ApiResponse> addComment(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskCommentRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Comment added")
                    .data(taskService.addComment(auth.getName(), workspaceId, taskId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping(value = "/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Đính kèm file")
    public ResponseEntity<ApiResponse> addAttachment(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Attachment uploaded")
                    .data(taskService.addAttachment(auth.getName(), workspaceId, taskId, file))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
