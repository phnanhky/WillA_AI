package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertBookingCallEventResponse {
    private Long id;
    private Long bookingId;
    private Long userId;
    private String userEmail;
    private String userName;
    private String eventType;
    private String roomName;
    private String clientSessionId;
    private String payload;
    private LocalDateTime createdAt;
}
