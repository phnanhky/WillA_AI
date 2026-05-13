package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceRequest {
    @NotBlank(message = "Workspace name cannot be empty")
    private String name;
    private String description;
}
