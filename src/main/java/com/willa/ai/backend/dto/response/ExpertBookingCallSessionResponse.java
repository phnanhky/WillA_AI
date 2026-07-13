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
public class ExpertBookingCallSessionResponse {
    private Long id;
    private Long bookingId;
    private Long userId;
    private String userEmail;
    private String userName;
    private String roomName;
    private String clientSessionId;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private Long durationSeconds;
}
