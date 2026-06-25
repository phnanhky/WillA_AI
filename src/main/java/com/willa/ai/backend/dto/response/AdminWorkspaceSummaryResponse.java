package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminWorkspaceSummaryResponse {

    private Long id;
    private String title;
    private String ownerName;
    private String ownerEmail;
}
