package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.request.WorkspaceNotesRequest;
import com.willa.ai.backend.dto.request.WorkspaceNoteMessageRequest;
import com.willa.ai.backend.dto.request.AddWorkspacePageRequest;
import com.willa.ai.backend.dto.request.UpdatePageDesignRequest;
import com.willa.ai.backend.dto.request.PageCommentRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.request.AcceptInviteRequest;
import com.willa.ai.backend.dto.request.UpdateMemberRoleRequest;
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
import org.springframework.data.domain.Page;

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
        try {
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Workspace created successfully").data(workspaceService.createWorkspace(auth.getName(), request)).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách Workspaces của user hiện tại")
    public ResponseEntity<ApiResponse> getWorkspaces(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Workspaces retrieved successfully").data(workspaceService.getUserWorkspaces(auth.getName())).build());
    }

    @DeleteMapping("/{workspaceId}")
    @Operation(summary = "Xóa Workspace (chỉ chủ sở hữu)")
    public ResponseEntity<ApiResponse> deleteWorkspace(
            Authentication auth,
            @PathVariable Long workspaceId) {
        try {
            workspaceService.deleteWorkspace(auth.getName(), workspaceId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace deleted successfully")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{workspaceId}")
    @Operation(summary = "Cập nhật tên / mô tả Workspace")
    public ResponseEntity<ApiResponse> updateWorkspace(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace updated successfully")
                    .data(workspaceService.updateWorkspace(auth.getName(), workspaceId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{workspaceId}/notes/messages")
    @Operation(summary = "Danh sách ghi chú chat của workspace")
    public ResponseEntity<ApiResponse> getWorkspaceNoteMessages(
            Authentication auth,
            @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Note messages retrieved")
                    .data(workspaceService.getWorkspaceNoteMessages(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{workspaceId}/notes/messages")
    @Operation(summary = "Gửi ghi chú chat vào workspace")
    public ResponseEntity<ApiResponse> addWorkspaceNoteMessage(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceNoteMessageRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Note message sent")
                    .data(workspaceService.addWorkspaceNoteMessage(auth.getName(), workspaceId, request.getContent()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PatchMapping("/{workspaceId}/notes")
    @Operation(summary = "Cập nhật ghi chú workspace (legacy)")
    public ResponseEntity<ApiResponse> updateWorkspaceNotes(
            Authentication auth,
            @PathVariable Long workspaceId,
            @RequestBody WorkspaceNotesRequest request) {
        try {
            String notes = request != null && request.getNotes() != null ? request.getNotes() : "";
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Notes updated successfully")
                    .data(workspaceService.updateWorkspaceNotes(auth.getName(), workspaceId, notes))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
    
    @GetMapping("/{workspaceId}/share")
    @Operation(summary = "Thông tin chia sẻ: thành viên + lời mời đang chờ")
    public ResponseEntity<ApiResponse> getWorkspaceShare(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Share info retrieved")
                    .data(workspaceService.getWorkspaceShare(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{workspaceId}/members")
    @Operation(summary = "Mời thành viên (thêm ngay nếu đã đăng ký, gửi email nếu chưa)")
    public ResponseEntity<ApiResponse> inviteMember(Authentication auth, @PathVariable Long workspaceId, @Valid @RequestBody InviteMemberRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Invite processed").data(workspaceService.inviteMember(auth.getName(), workspaceId, request)).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{workspaceId}/invites")
    @Operation(summary = "Danh sách lời mời đang chờ")
    public ResponseEntity<ApiResponse> getPendingInvites(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Pending invites retrieved")
                    .data(workspaceService.getPendingInvites(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{workspaceId}/invites/{inviteId}")
    @Operation(summary = "Thu hồi lời mời")
    public ResponseEntity<ApiResponse> revokeInvite(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long inviteId) {
        try {
            workspaceService.revokeInvite(auth.getName(), workspaceId, inviteId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Invite revoked").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/invites/accept")
    @Operation(summary = "Chấp nhận lời mời workspace qua token")
    public ResponseEntity<ApiResponse> acceptInvite(Authentication auth, @Valid @RequestBody AcceptInviteRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Invite accepted")
                    .data(workspaceService.acceptInvite(auth.getName(), request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    @Operation(summary = "Xóa thành viên khỏi workspace")
    public ResponseEntity<ApiResponse> removeMember(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long memberId) {
        try {
            workspaceService.removeMember(auth.getName(), workspaceId, memberId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Member removed").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PatchMapping("/{workspaceId}/members/{memberId}")
    @Operation(summary = "Cập nhật quyền thành viên")
    public ResponseEntity<ApiResponse> updateMemberRole(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Member role updated")
                    .data(workspaceService.updateMemberRole(auth.getName(), workspaceId, memberId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
    
    @GetMapping("/{workspaceId}/members")
    @Operation(summary = "Lấy danh sách thành viên trong Workspace")
    public ResponseEntity<ApiResponse> getMembers(Authentication auth, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Members retrieved successfully").data(workspaceService.getWorkspaceMembers(auth.getName(), workspaceId)).build());
    }

    @PostMapping("/{workspaceId}/pages")
    @Operation(summary = "Thêm mới hoặc cập nhật một trang thiết kế (Page) trong Workspace")
    public ResponseEntity<ApiResponse> addPage(Authentication auth, @PathVariable Long workspaceId, @Valid @RequestBody AddWorkspacePageRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Page added successfully").data(workspaceService.addPageToWorkspace(auth.getName(), workspaceId, request)).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{workspaceId}/pages")
    @Operation(summary = "Lấy danh sách các trang thiết kế của Workspace")
    public ResponseEntity<ApiResponse> getPages(Authentication auth, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Pages retrieved successfully").data(workspaceService.getWorkspacePages(auth.getName(), workspaceId)).build());
    }

    @PatchMapping("/{workspaceId}/pages/{pageId}/design")
    @Operation(summary = "Lưu layer, hướng trang (design JSON) cho một Page")
    public ResponseEntity<ApiResponse> updatePageDesign(
            Authentication auth,
            @PathVariable Long workspaceId,
            @PathVariable Long pageId,
            @Valid @RequestBody UpdatePageDesignRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Page design saved")
                    .data(workspaceService.updatePageDesign(
                            auth.getName(), workspaceId, pageId, request.getDesignData()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{workspaceId}/pages/{pageId}")
    @Operation(summary = "Xóa một trang thiết kế của Workspace")
    public ResponseEntity<ApiResponse> deletePage(Authentication auth, @PathVariable Long workspaceId, @PathVariable Long pageId) {
        try {
            workspaceService.deleteWorkspacePage(auth.getName(), workspaceId, pageId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Page deleted successfully").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{workspaceId}/pages/reorder")
    @Operation(summary = "Sắp xếp lại thứ tự các trang thiết kế")
    public ResponseEntity<ApiResponse> reorderPages(Authentication auth, @PathVariable Long workspaceId, @RequestBody java.util.List<Long> pageIds) {
        try {
            workspaceService.reorderPages(auth.getName(), workspaceId, pageIds);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Pages reordered successfully").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/pages/{pageId}/comments")
    @Operation(summary = "Tạo Comment ghim trên ảnh (Chỉ EDITOR, ADMIN)")
    public ResponseEntity<ApiResponse> addComment(Authentication auth, @PathVariable Long pageId, @Valid @RequestBody PageCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Comment added").data(workspaceService.addComment(auth.getName(), pageId, request)).build());
    }

    @GetMapping("/pages/{pageId}/comments")
    @Operation(summary = "Lấy danh sách các Comments của một Page")
    public ResponseEntity<ApiResponse> getComments(Authentication auth, @PathVariable Long pageId) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Comments retrieved").data(workspaceService.getComments(auth.getName(), pageId)).build());
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Xóa Comment (ADMIN xóa mọi comment, EDITOR chỉ xóa của mình)")
    public ResponseEntity<ApiResponse> deleteComment(Authentication auth, @PathVariable Long commentId) {
        workspaceService.deleteComment(auth.getName(), commentId);
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Comment deleted").build());
    }

    @PostMapping("/{workspaceId}/share")
    @Operation(summary = "Chia sẻ Workspace lên Cộng đồng (Chỉ Owner/Admin)")
    public ResponseEntity<ApiResponse> shareToCommunity(Authentication auth, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Workspace shared to community successfully").data(workspaceService.shareToCommunity(auth.getName(), workspaceId)).build());
    }

    @GetMapping("/explore")
    @Operation(summary = "Khám phá các dự án chia sẻ cho Cộng đồng")
    public ResponseEntity<ApiResponse> exploreCommunity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.builder().status(true).message("Community workspaces retrieved").data(workspaceService.exploreWorkspaces(page, size)).build());
    }
}
