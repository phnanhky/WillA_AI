package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertBookingCallHistoryResponse {
    /** Tổng thời lượng các phiên đã kết thúc (seconds). */
    private Long totalDurationSeconds;
    private Integer sessionCount;
    private List<ExpertBookingCallSessionResponse> sessions;
    /**
     * Event chi tiết Jitsi — chỉ trả về cho admin.
     * Participant API luôn để rỗng / null.
     */
    private List<ExpertBookingCallEventResponse> events;
}
