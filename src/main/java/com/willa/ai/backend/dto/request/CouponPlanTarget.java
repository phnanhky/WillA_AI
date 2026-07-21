package com.willa.ai.backend.dto.request;

import lombok.Data;

@Data
public class CouponPlanTarget {
    private Long planId;
    /** FEEDBACK | WORKSPACE */
    private String planType;
}
