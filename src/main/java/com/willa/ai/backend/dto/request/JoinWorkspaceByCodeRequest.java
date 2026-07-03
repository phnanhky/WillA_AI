package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinWorkspaceByCodeRequest {
    @NotBlank
    private String inviteCode;

    /** CODE hoặc QR — mặc định CODE */
    private String joinSource;
}
