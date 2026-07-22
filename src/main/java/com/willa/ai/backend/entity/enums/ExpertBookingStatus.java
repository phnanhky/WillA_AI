package com.willa.ai.backend.entity.enums;

public enum ExpertBookingStatus {
    PENDING_EXPERT,
    PENDING_PAYMENT,
    AWAITING_EXPERT,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    /** Hết SLA Accept / không nhận đơn — đã (hoặc chờ) hoàn tiền. */
    EXPIRED
}
