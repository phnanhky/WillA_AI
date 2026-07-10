package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBrandKitProfileRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private Long workspaceId;
}
