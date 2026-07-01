package com.willa.ai.backend.dto.response;

import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import com.willa.ai.backend.entity.enums.ExpertBookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertBookingResponse {

    private Long id;
    private ExpertBookingType bookingType;
    private ExpertBookingStatus status;
    private String brief;
    private String publications;
    private List<String> driveLinks;
    private List<ExpertBookingAttachmentResponse> attachments;
    private String expertFeedback;
    private Integer hourlyHours;
    private Long amountVnd;
    private Long parentBookingId;
    private Long expertId;
    private String expertName;
    private String expertEmail;
    private String clientName;
    private String clientEmail;
    private Long orderCode;
    private String meetingRoomUrl;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
