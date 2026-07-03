package com.willa.ai.backend.dto.request;

import lombok.Data;

@Data
public class ValidateCouponRequest {
    private String code;
    private Long planId;
    /** FEEDBACK | WORKSPACE */
    private String planType;
}
