package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.InviteStatus;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkspaceInviteResponse {
    private Long id;
    private Long workspaceId;
    private String workspaceName;
    private String email;
    private WorkspaceRole role;
    private InviteStatus status;
    private String invitedByName;
    private String inviteLink;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
