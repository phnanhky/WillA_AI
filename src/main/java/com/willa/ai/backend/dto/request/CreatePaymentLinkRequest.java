package com.willa.ai.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentLinkRequest {
    private Long planId;
    /** FEEDBACK | WORKSPACE */
    private String planType;
    private String couponCode;
    /** Thông tin khách trên đơn — quản lý order. */
    private String name;
    private String phone;
    private String email;
}
