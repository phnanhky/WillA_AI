package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.CouponDiscountType;
import com.willa.ai.backend.entity.enums.CouponPlanScope;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CouponRequest {
    private String code;
    private CouponDiscountType discountType;
    private Long discountValue;
    private CouponPlanScope planScope;
    private Long planId;
    private String note;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    /** true = server tự sinh mã nếu code trống */
    private Boolean autoGenerateCode;
}
