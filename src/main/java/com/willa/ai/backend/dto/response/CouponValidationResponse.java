package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.CouponDiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponValidationResponse {
    private boolean valid;
    private String code;
    private CouponDiscountType discountType;
    private Long discountValue;
    private Long originalAmount;
    private Long discountAmount;
    private Long finalAmount;
    /** Giá thanh toán nếu không dùng coupon (giảm giá admin). */
    private Long adminDiscountPrice;
    private String message;
}
