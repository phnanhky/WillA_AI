package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.dto.CouponPlanTarget;
import com.willa.ai.backend.entity.enums.CouponDiscountType;
import com.willa.ai.backend.entity.enums.CouponPlanScope;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CouponRequest {
    private String code;
    private CouponDiscountType discountType;
    private Long discountValue;
    /** Số ngày cộng thêm vào thời hạn gói khi đổi mã (MONTHLY/YEARLY). */
    private Integer bonusDays;
    private CouponPlanScope planScope;
    /** @deprecated dùng eligiblePlans */
    private Long planId;
    private List<CouponPlanTarget> eligiblePlans;
    private List<Long> allowedUserIds;
    private String note;
    private Boolean isActive;
    /** Chỉ ngày — service map sang 00:00:00 */
    private LocalDate startsAt;
    /** Chỉ ngày — service map sang 23:59:59 */
    private LocalDate expiresAt;
    /** null = không giới hạn lượt dùng */
    private Integer maxRedemptions;
    /** true = xóa giới hạn lượt dùng khi cập nhật */
    private Boolean clearMaxRedemptions;
    /** true = server tự sinh mã nếu code trống */
    private Boolean autoGenerateCode;
}
