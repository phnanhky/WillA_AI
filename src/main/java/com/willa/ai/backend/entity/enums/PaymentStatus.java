package com.willa.ai.backend.entity.enums;

public enum PaymentStatus {
    PENDING,
    PAID,
    CANCELLED,
    FAILED,
    /** Đã yêu cầu hoàn (SLA/reject) — ops cần hoàn trên PayOS dashboard. */
    REFUND_PENDING,
    /** Ops đã xác nhận hoàn xong trên PayOS. */
    REFUNDED
}
