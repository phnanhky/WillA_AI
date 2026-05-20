package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddUserLibraryImageRequest {
    @NotBlank(message = "imageUrl is required")
    private String imageUrl;

    @NotNull(message = "fileSizeBytes is required")
    private Long fileSizeBytes;
}
