package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.BillingCycle;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspacePlanRequest {

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    private BigDecimal price;

    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle;

    private Double discountPercentage;

    /** null = unlimited */
    private Integer maxOwnedWorkspaces;

    /** null = unlimited */
    private Integer maxMembersPerWorkspace;

    private Boolean isActive;

    private Boolean isDefault;

    private Integer sortOrder;
}
