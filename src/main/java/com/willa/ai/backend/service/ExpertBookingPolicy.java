package com.willa.ai.backend.service;

/**
 * Policy cố định cho Expert Review / Hourly — SLA Accept, Q&A, call minutes.
 */
public final class ExpertBookingPolicy {

    private ExpertBookingPolicy() {}

    /** Expert phải Accept trong bao lâu sau khi thanh toán. */
    public static final int ACCEPT_SLA_HOURS = 24;

    /** Sau khi có feedback chính, client được hỏi làm rõ trong bao lâu. */
    public static final int REVIEW_QA_HOURS = 48;

    /** Số tin nhắn client tối đa trong cửa sổ Q&A (sau feedback). */
    public static final int REVIEW_QA_CLIENT_MESSAGES = 8;

    /** Call tối đa trong gói REVIEW (phút). Cần dài hơn → upsell HOURLY. */
    public static final int REVIEW_CALL_MINUTES = 15;

    public static int callMinutesForHourly(Integer hours) {
        int h = hours != null && hours > 0 ? hours : 1;
        return h * 60;
    }

    public static int callMinutesFor(com.willa.ai.backend.entity.enums.ExpertBookingType type, Integer hours) {
        if (type == com.willa.ai.backend.entity.enums.ExpertBookingType.HOURLY) {
            return callMinutesForHourly(hours);
        }
        return REVIEW_CALL_MINUTES;
    }
}
