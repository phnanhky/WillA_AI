package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceInvitePreviewResponse {
    private boolean valid;
    private boolean expired;
    private Long workspaceId;
    private String workspaceName;
    private String inviterName;
    private String inviteEmail;
    private String message;
}
