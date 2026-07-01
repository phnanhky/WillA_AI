package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.payos.type.CheckoutResponseData;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertBookingCheckoutResponse {

    private ExpertBookingResponse booking;
    private CheckoutResponseData checkout;
}
