package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.CouponDiscountType;
import com.willa.ai.backend.entity.enums.CouponPlanScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponResponse {
    private Long id;
    private String code;
    private CouponDiscountType discountType;
    private Long discountValue;
    private CouponPlanScope planScope;
    private Long planId;
    private String note;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    private Boolean redeemed;
    private LocalDateTime redeemedAt;
    private String redeemedByEmail;
    private Long redeemedPaymentId;
    private LocalDateTime createdAt;
}
