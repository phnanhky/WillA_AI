package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskChecklistRequest {
    @NotBlank
    private String title;
}
