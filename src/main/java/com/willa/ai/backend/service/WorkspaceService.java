package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.WorkspaceRequest;
import com.willa.ai.backend.dto.request.InviteMemberRequest;
import com.willa.ai.backend.dto.response.WorkspaceResponse;
import com.willa.ai.backend.dto.response.WorkspaceMemberResponse;
import com.willa.ai.backend.dto.response.WorkspaceShareResponse;
import com.willa.ai.backend.dto.response.WorkspaceInviteResponse;
import com.willa.ai.backend.dto.response.InviteMemberResultResponse;
import com.willa.ai.backend.dto.request.AcceptInviteRequest;
import com.willa.ai.backend.dto.request.UpdateMemberRoleRequest;

import com.willa.ai.backend.dto.request.AddWorkspacePageRequest;
import com.willa.ai.backend.dto.response.WorkspacePageResponse;
import com.willa.ai.backend.dto.request.PageCommentRequest;
import com.willa.ai.backend.dto.response.PageCommentResponse;
import com.willa.ai.backend.dto.response.WorkspaceNoteMessageResponse;
import java.util.List;
import org.springframework.data.domain.Page;

public interface WorkspaceService {
    WorkspaceResponse createWorkspace(String email, WorkspaceRequest request);
    WorkspaceResponse updateWorkspace(String email, Long workspaceId, WorkspaceRequest request);
    WorkspaceResponse updateWorkspaceNotes(String email, Long workspaceId, String notes);
    List<WorkspaceNoteMessageResponse> getWorkspaceNoteMessages(String email, Long workspaceId);
    WorkspaceNoteMessageResponse addWorkspaceNoteMessage(String email, Long workspaceId, String content);
    void deleteWorkspace(String email, Long workspaceId);
    List<WorkspaceResponse> getUserWorkspaces(String email);
    InviteMemberResultResponse inviteMember(String email, Long workspaceId, InviteMemberRequest request);
    WorkspaceShareResponse getWorkspaceShare(String email, Long workspaceId);
    List<WorkspaceInviteResponse> getPendingInvites(String email, Long workspaceId);
    void revokeInvite(String email, Long workspaceId, Long inviteId);
    WorkspaceMemberResponse acceptInvite(String email, AcceptInviteRequest request);
    void removeMember(String email, Long workspaceId, Long memberId);
    WorkspaceMemberResponse updateMemberRole(String email, Long workspaceId, Long memberId, UpdateMemberRoleRequest request);
    List<WorkspaceMemberResponse> getWorkspaceMembers(String email, Long workspaceId);
    WorkspacePageResponse addPageToWorkspace(String email, Long workspaceId, AddWorkspacePageRequest request);
    List<WorkspacePageResponse> getWorkspacePages(String email, Long workspaceId);
    WorkspacePageResponse updatePageDesign(String email, Long workspaceId, Long pageId, String designData);
    void deleteWorkspacePage(String email, Long workspaceId, Long pageId);
    void reorderPages(String email, Long workspaceId, List<Long> pageIds);
    PageCommentResponse addComment(String email, Long pageId, PageCommentRequest request);
    List<PageCommentResponse> getComments(String email, Long pageId);
    void deleteComment(String email, Long commentId);
    WorkspaceResponse shareToCommunity(String email, Long workspaceId);
    Page<WorkspaceResponse> exploreWorkspaces(int page, int size);
}
