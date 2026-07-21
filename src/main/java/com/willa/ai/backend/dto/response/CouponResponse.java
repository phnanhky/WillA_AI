package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.dto.CouponPlanTarget;
import com.willa.ai.backend.entity.enums.CouponDiscountType;
import com.willa.ai.backend.entity.enums.CouponPlanScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponResponse {
    private Long id;
    private String code;
    private CouponDiscountType discountType;
    private Long discountValue;
    private Integer bonusDays;
    private CouponPlanScope planScope;
    /** @deprecated dùng eligiblePlans */
    private Long planId;
    private List<CouponPlanTarget> eligiblePlans;
    private List<Long> allowedUserIds;
    private List<String> allowedUserEmails;
    private String note;
    private Boolean isActive;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private Integer maxRedemptions;
    private Integer redemptionCount;
    private Integer remainingUses;
    private Boolean redeemed;
    private LocalDateTime redeemedAt;
    private String redeemedByEmail;
    private Long redeemedPaymentId;
    private LocalDateTime createdAt;
}
