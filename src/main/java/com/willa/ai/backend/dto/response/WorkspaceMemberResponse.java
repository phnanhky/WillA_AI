package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.WorkspaceJoinSource;
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
    private String avatarUrl;
    private String email;
    private WorkspaceRole role;
    private Boolean isImportant;
    private LocalDateTime joinedAt;
    private WorkspaceJoinSource joinSource;
    private LocalDateTime firstActiveAt;
    private LocalDateTime lastActiveAt;
    private Boolean activated;
}
