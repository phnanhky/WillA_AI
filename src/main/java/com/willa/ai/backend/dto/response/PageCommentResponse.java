
package com.willa.ai.backend.dto.response;
import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
public class PageCommentResponse {
    private Long id;
    private Long pageId;
    private Long userId;
    private String userName;
    private String content;
    private Double coordinateX;
    private Double coordinateY;
    private Boolean resolved;
    private LocalDateTime createdAt;
}
