package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertBookingMessageResponse {

    private Long id;
    private Long senderId;
    private String senderName;
    private String senderEmail;
    private String content;
    private LocalDateTime createdAt;
}
