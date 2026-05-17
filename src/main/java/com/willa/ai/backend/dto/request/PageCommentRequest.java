
package com.willa.ai.backend.dto.request;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class PageCommentRequest {
    @NotBlank(message="Content can not be empty")
    private String content;
    private Double coordinateX;
    private Double coordinateY;
}
