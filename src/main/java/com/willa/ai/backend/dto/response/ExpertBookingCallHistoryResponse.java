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
    private List<ExpertBookingCallSessionResponse> sessions;
    private List<ExpertBookingCallEventResponse> events;
}
