package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.TaskAttachmentUpdateRequest;
import com.willa.ai.backend.dto.request.TaskChecklistItemRequest;
import com.willa.ai.backend.dto.request.TaskChecklistRequest;
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
    public ResponseEntity<ApiResponse> listTasks(
            Authentication auth,
            @PathVariable Long workspaceId,
            @RequestParam(required = false) Long projectId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Tasks retrieved")
                    .data(taskService.listTasks(auth.getName(), workspaceId, projectId))
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

    @PatchMapping("/{taskId}/attachments/{attachmentId}")
    @Operation(summary = "Đổi tên tệp đính kèm")
    public ResponseEntity<ApiResponse> updateAttachment(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @PathVariable Long attachmentId,
            @Valid @RequestBody TaskAttachmentUpdateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Attachment updated")
                    .data(taskService.updateAttachment(
                            auth.getName(), workspaceId, taskId, attachmentId, request.getFileName()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{taskId}/attachments/{attachmentId}")
    @Operation(summary = "Xóa tệp đính kèm")
    public ResponseEntity<ApiResponse> deleteAttachment(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @PathVariable Long attachmentId) {
        try {
            taskService.deleteAttachment(auth.getName(), workspaceId, taskId, attachmentId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Attachment deleted").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{taskId}/google-meet")
    @Operation(summary = "Tạo link Google Meet cho task")
    public ResponseEntity<ApiResponse> createGoogleMeet(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Google Meet created")
                    .data(taskService.createGoogleMeet(auth.getName(), workspaceId, taskId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{taskId}/google-meet")
    @Operation(summary = "Gỡ link Google Meet")
    public ResponseEntity<ApiResponse> removeGoogleMeet(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Google Meet removed")
                    .data(taskService.removeGoogleMeet(auth.getName(), workspaceId, taskId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{taskId}/checklists")
    @Operation(summary = "Danh sách checklist của task")
    public ResponseEntity<ApiResponse> listChecklists(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Checklists retrieved")
                    .data(taskService.listChecklists(auth.getName(), workspaceId, taskId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{taskId}/checklists")
    @Operation(summary = "Tạo checklist")
    public ResponseEntity<ApiResponse> createChecklist(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskChecklistRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Checklist created")
                    .data(taskService.createChecklist(auth.getName(), workspaceId, taskId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{taskId}/checklists/{checklistId}")
    @Operation(summary = "Cập nhật tiêu đề checklist")
    public ResponseEntity<ApiResponse> updateChecklist(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId,
            @Valid @RequestBody TaskChecklistRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Checklist updated")
                    .data(taskService.updateChecklist(auth.getName(), workspaceId, taskId, checklistId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{taskId}/checklists/{checklistId}")
    @Operation(summary = "Xóa checklist")
    public ResponseEntity<ApiResponse> deleteChecklist(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId) {
        try {
            taskService.deleteChecklist(auth.getName(), workspaceId, taskId, checklistId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Checklist deleted").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{taskId}/checklists/{checklistId}/items")
    @Operation(summary = "Thêm mục checklist")
    public ResponseEntity<ApiResponse> addChecklistItem(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId,
            @RequestBody TaskChecklistItemRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Checklist item added")
                    .data(taskService.addChecklistItem(auth.getName(), workspaceId, taskId, checklistId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{taskId}/checklists/{checklistId}/items/{itemId}")
    @Operation(summary = "Cập nhật mục checklist")
    public ResponseEntity<ApiResponse> updateChecklistItem(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId,
            @PathVariable Long itemId,
            @RequestBody TaskChecklistItemRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Checklist item updated")
                    .data(taskService.updateChecklistItem(auth.getName(), workspaceId, taskId, checklistId, itemId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{taskId}/checklists/{checklistId}/items/{itemId}")
    @Operation(summary = "Xóa mục checklist")
    public ResponseEntity<ApiResponse> deleteChecklistItem(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId,
            @PathVariable Long itemId) {
        try {
            taskService.deleteChecklistItem(auth.getName(), workspaceId, taskId, checklistId, itemId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Checklist item deleted").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
