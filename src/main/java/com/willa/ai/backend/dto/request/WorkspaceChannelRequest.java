package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkspaceChannelRequest {
    @NotBlank
    @Size(max = 100)
    private String name;
}
