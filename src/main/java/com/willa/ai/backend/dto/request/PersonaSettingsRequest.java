package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PersonaSettingsRequest {

    @NotNull(message = "enabled is required")
    private Boolean enabled;
}
