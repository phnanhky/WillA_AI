package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.AcceptInviteRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.request.JoinWorkspaceByCodeRequest;
import com.willa.ai.backend.dto.request.UpdateMemberImportantRequest;
import com.willa.ai.backend.dto.request.UpdateMemberRoleRequest;
import com.willa.ai.backend.dto.request.WorkspaceRequest;
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
@Tag(name = "Workspace", description = "Quản lý workspace chia task")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "Tạo workspace mới")
    public ResponseEntity<ApiResponse> createWorkspace(Authentication auth, @Valid @RequestBody WorkspaceRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace created successfully")
                    .data(workspaceService.createWorkspace(auth.getName(), request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping
    @Operation(summary = "Danh sách workspace của user")
    public ResponseEntity<ApiResponse> getWorkspaces(Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspaces retrieved successfully")
                    .data(workspaceService.getUserWorkspaces(auth.getName()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{workspaceId}")
    @Operation(summary = "Cập nhật workspace")
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

    @DeleteMapping("/{workspaceId}")
    @Operation(summary = "Xóa workspace (chỉ chủ sở hữu)")
    public ResponseEntity<ApiResponse> deleteWorkspace(Authentication auth, @PathVariable Long workspaceId) {
        try {
            workspaceService.deleteWorkspace(auth.getName(), workspaceId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Workspace deleted successfully").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/join")
    @Operation(summary = "Tham gia workspace bằng mã mời")
    public ResponseEntity<ApiResponse> joinByCode(Authentication auth, @Valid @RequestBody JoinWorkspaceByCodeRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Joined workspace successfully")
                    .data(workspaceService.joinByInviteCode(auth.getName(), request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PatchMapping("/{workspaceId}/important")
    @Operation(summary = "Đánh dấu workspace quan trọng cho bản thân")
    public ResponseEntity<ApiResponse> setImportant(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody UpdateMemberImportantRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Workspace importance updated")
                    .data(workspaceService.setImportant(auth.getName(), workspaceId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{workspaceId}/share")
    @Operation(summary = "Thông tin chia sẻ: mã mời, thành viên, lời mời đang chờ")
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
    @Operation(summary = "Mời thành viên")
    public ResponseEntity<ApiResponse> inviteMember(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody InviteMemberRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Invite processed")
                    .data(workspaceService.inviteMember(auth.getName(), workspaceId, request))
                    .build());
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
    @Operation(summary = "Chấp nhận lời mời qua token email")
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

    @GetMapping("/{workspaceId}/members")
    @Operation(summary = "Danh sách thành viên")
    public ResponseEntity<ApiResponse> getMembers(Authentication auth, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Members retrieved successfully")
                .data(workspaceService.getWorkspaceMembers(auth.getName(), workspaceId))
                .build());
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    @Operation(summary = "Xóa thành viên")
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
}
