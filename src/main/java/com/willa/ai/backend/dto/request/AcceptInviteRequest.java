package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcceptInviteRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
