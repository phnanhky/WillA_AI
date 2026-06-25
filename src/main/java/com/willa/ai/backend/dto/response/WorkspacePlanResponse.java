package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.BillingCycle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspacePlanResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private BillingCycle billingCycle;
    private Double discountPercentage;
    private BigDecimal promotionalPrice;
    private Integer maxOwnedWorkspaces;
    private Integer maxMembersPerWorkspace;
    private Boolean isActive;
    private Boolean isDefault;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
