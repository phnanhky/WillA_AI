
package com.willa.ai.backend.dto.response;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class WorkspacePageResponse {
    private Long id;
    private Long workspaceId;
    private Integer pageNumber;
    private String imageUrl;
    private Long fileSizeBytes;
    /** JSON: layers, orientation, ... */
    private String designData;
}
