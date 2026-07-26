package com.willa.ai.backend.dto.request;

import lombok.Data;

@Data
public class AdminExpertRefundRequest {
    /** Lý do hỗ trợ khách (hiển thị trong hàng đợi ops). */
    private String reason;
}
