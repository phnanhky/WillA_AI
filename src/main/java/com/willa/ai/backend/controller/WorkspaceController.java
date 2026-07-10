package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.AcceptInviteRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.request.JoinWorkspaceByCodeRequest;
import com.willa.ai.backend.dto.request.UpdateMemberImportantRequest;
import com.willa.ai.backend.dto.request.UpdateMemberRoleRequest;
import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.WorkspaceService;
import com.willa.ai.backend.service.WorkspaceMemberStatsService;
import com.willa.ai.backend.service.WorkspaceChannelService;
import com.willa.ai.backend.service.TaskService;
import com.willa.ai.backend.service.ExpertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workspace", description = "Quản lý workspace chia task")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberStatsService workspaceMemberStatsService;
    private final WorkspaceChannelService workspaceChannelService;
    private final TaskService taskService;
    private final ExpertService expertService;

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

    @GetMapping("/invites/preview")
    @Operation(summary = "Xem trước lời mời workspace (public)")
    public ResponseEntity<ApiResponse> previewInvite(@RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Invite preview")
                .data(workspaceService.getInvitePreview(token))
                .build());
    }

    @GetMapping("/{workspaceId}/member-stats")
    @Operation(summary = "Thống kê hiệu suất thành viên trong workspace")
    public ResponseEntity<ApiResponse> getMemberStats(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Member stats retrieved")
                    .data(workspaceMemberStatsService.getMemberStats(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{workspaceId}/hub/comments")
    @Operation(summary = "Bình luận liên quan tới My Space (assignee, reply, thread)")
    public ResponseEntity<ApiResponse> listHubComments(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Hub comments retrieved")
                    .data(taskService.listHubComments(auth.getName(), workspaceId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{workspaceId}/hub/channel-messages")
    @Operation(summary = "Tin nhắn kênh gần đây (cho @mention inbox)")
    public ResponseEntity<ApiResponse> listHubChannelMessages(Authentication auth, @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Hub channel messages retrieved")
                    .data(workspaceChannelService.listHubChannelMessages(auth.getName(), workspaceId))
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

    @PostMapping("/{workspaceId}/activity")
    @Operation(summary = "Ghi nhận hoạt động thành viên trong workspace")
    public ResponseEntity<ApiResponse> recordActivity(Authentication auth, @PathVariable Long workspaceId) {
        try {
            workspaceService.recordMemberActivity(auth.getName(), workspaceId);
            return ResponseEntity.ok(ApiResponse.builder().status(true).message("Activity recorded").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
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

    @PostMapping("/{workspaceId}/leave")
    @Operation(summary = "Rời workspace (thành viên)")
    public ResponseEntity<ApiResponse> leaveWorkspace(Authentication auth, @PathVariable Long workspaceId) {
        try {
            workspaceService.leaveWorkspace(auth.getName(), workspaceId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Left workspace successfully")
                    .build());
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
    @PostMapping("/{workspaceId}/chat-extract")
    @Operation(summary = "Trích xuất task từ tin nhắn chat (dùng xAI)")
    public ResponseEntity<ApiResponse> extractTaskFromChat(
            Authentication auth,
            @PathVariable Long workspaceId,
            @Valid @RequestBody com.willa.ai.backend.dto.request.WorkspaceChatExtractRequest request) {
        try {
            com.willa.ai.backend.dto.response.WorkspaceChatExtractResponse extracted = workspaceService.extractTaskFromChat(auth.getName(), workspaceId, request);
            try {
                log.info("FINAL DTO: {}", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extracted));
            } catch(Exception e) {}
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Chat extracted")
                    .data(extracted)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{workspaceId}/experts")
    @Operation(summary = "Danh sách expert (toàn app — không phụ thuộc workspace)")
    public ResponseEntity<ApiResponse> getWorkspaceExperts(
            Authentication auth,
            @PathVariable Long workspaceId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Experts retrieved successfully")
                    .data(expertService.listActiveExperts())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
