package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBrandKitProfileRequest {

    @NotBlank
    @Size(max = 200)
    private String title;
}
