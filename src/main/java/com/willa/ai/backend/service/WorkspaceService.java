package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.response.WorkspaceResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberResponse;

import java.util.List;

public interface WorkspaceService {
    WorkspaceResponse createWorkspace(String email, WorkspaceRequest request);
    List<WorkspaceResponse> getUserWorkspaces(String email);
    WorkspaceMemberResponse inviteMember(String email, Long workspaceId, InviteMemberRequest request);
    List<WorkspaceMemberResponse> getWorkspaceMembers(String email, Long workspaceId);
}
