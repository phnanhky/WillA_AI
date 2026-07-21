package com.willa.ai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Gói áp dụng coupon — FEEDBACK hoặc WORKSPACE. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponPlanTarget {
    /** FEEDBACK | WORKSPACE */
    private String planType;
    private Long planId;
    /** Tên gói (response only). */
    private String planName;
}
