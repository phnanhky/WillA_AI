package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BrandKitCheckSummaryResponse {
    private Long id;
    private Long profileId;
    private String profileTitle;
    private String status;
    private BigDecimal avgBrandScore;
    private Integer totalAssets;
    private LocalDateTime createdAt;
}
