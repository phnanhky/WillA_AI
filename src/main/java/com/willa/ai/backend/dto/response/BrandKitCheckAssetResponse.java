package com.willa.ai.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BrandKitCheckAssetResponse {
    private Long id;
    private String imageUrl;
    private String fileName;
    private BigDecimal brandScore;
    private String severity;
}
