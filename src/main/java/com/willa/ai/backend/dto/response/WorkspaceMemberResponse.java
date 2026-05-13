package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.WorkspaceRole;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class WorkspaceMemberResponse {
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String userName;
    private String email;
    private WorkspaceRole role;
    private LocalDateTime joinedAt;
}
