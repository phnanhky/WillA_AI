package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertBookingFeedbackRequest {

    private ExpertBookingStatus status;
    private String feedback;
}
