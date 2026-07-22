package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmResponse {
    private Long orderCode;
    private String paymentStatus;
    private boolean activated;
    private Long expertBookingId;
    private String expertBookingStatus;
    /** Gợi ý redirect FE sau khi confirm (vd /experts/orders). */
    private String suggestedRedirect;
    private String message;
}
