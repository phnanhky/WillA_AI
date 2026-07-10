package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BrandKitCheckDetailResponse {
    private Long id;
    private Long profileId;
    private String profileTitle;
    private String status;
    private BigDecimal avgBrandScore;
    private Integer totalAssets;
    private Object report;
    private List<BrandKitCheckAssetResponse> assets;
    private LocalDateTime createdAt;
}
