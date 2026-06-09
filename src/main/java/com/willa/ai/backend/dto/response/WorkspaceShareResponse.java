package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkspaceShareResponse {
    private Long workspaceId;
    private String workspaceName;
    private Long ownerId;
    private String ownerName;
    private String inviteCode;
    private String inviteLink;
    private String qrCodeUrl;
    private List<WorkspaceMemberResponse> members;
    private List<WorkspaceInviteResponse> pendingInvites;
    private boolean canManageMembers;
}
