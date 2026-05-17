
package com.willa.ai.backend.dto.request;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddWorkspacePageRequest {
    @NotNull(message = "Page number is required")
    @Min(value = 1, message = "Page number must be at least 1")
    private Integer pageNumber;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    @NotNull(message = "File size is required")
    @Min(value = 0, message = "File size cannot be negative")
    private Long fileSizeBytes;
}
