package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRedemptionResponse {
    private Long id;
    private String userEmail;
    private Long paymentId;
    private LocalDateTime redeemedAt;
}
