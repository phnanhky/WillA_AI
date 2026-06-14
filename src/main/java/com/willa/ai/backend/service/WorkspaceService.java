package com.willa.ai.backend.service;

import java.util.List;

import com.willa.ai.backend.dto.request.AcceptInviteRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.request.JoinWorkspaceByCodeRequest;
import com.willa.ai.backend.dto.request.UpdateMemberImportantRequest;
import com.willa.ai.backend.dto.request.UpdateMemberRoleRequest;
import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.response.InviteMemberResultResponse;
import com.willa.ai.backend.dto.response.WorkspaceInviteResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberResponse;
import com.willa.ai.backend.dto.response.WorkspaceResponse;
import com.willa.ai.backend.dto.response.WorkspaceShareResponse;

public interface WorkspaceService {
    WorkspaceResponse createWorkspace(String email, WorkspaceRequest request);

    WorkspaceResponse updateWorkspace(String email, Long workspaceId, WorkspaceRequest request);

    void deleteWorkspace(String email, Long workspaceId);

    List<WorkspaceResponse> getUserWorkspaces(String email);

    WorkspaceMemberResponse joinByInviteCode(String email, JoinWorkspaceByCodeRequest request);

    WorkspaceResponse setImportant(String email, Long workspaceId, UpdateMemberImportantRequest request);

    InviteMemberResultResponse inviteMember(String email, Long workspaceId, InviteMemberRequest request);

    WorkspaceShareResponse getWorkspaceShare(String email, Long workspaceId);

    List<WorkspaceInviteResponse> getPendingInvites(String email, Long workspaceId);

    void revokeInvite(String email, Long workspaceId, Long inviteId);

    WorkspaceMemberResponse acceptInvite(String email, AcceptInviteRequest request);

    void removeMember(String email, Long workspaceId, Long memberId);

    WorkspaceMemberResponse updateMemberRole(String email, Long workspaceId, Long memberId, UpdateMemberRoleRequest request);

    List<WorkspaceMemberResponse> getWorkspaceMembers(String email, Long workspaceId);

    com.willa.ai.backend.dto.response.WorkspaceChatExtractResponse extractTaskFromChat(String email, Long workspaceId, com.willa.ai.backend.dto.request.WorkspaceChatExtractRequest request);
}
