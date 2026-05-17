package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    @NotNull(message = "Role is required")
    private WorkspaceRole role;
}
