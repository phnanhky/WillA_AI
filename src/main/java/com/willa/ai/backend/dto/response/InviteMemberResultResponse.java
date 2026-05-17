package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InviteMemberResultResponse {
    /** MEMBER_ADDED | INVITE_SENT */
    private String resultType;
    private String message;
    private WorkspaceMemberResponse member;
    private WorkspaceInviteResponse invite;
}
