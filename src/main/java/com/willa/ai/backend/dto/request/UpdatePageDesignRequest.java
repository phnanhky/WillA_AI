package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePageDesignRequest {
    @NotBlank(message = "designData is required")
    private String designData;
}
