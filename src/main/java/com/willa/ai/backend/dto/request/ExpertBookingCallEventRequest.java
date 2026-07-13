package com.willa.ai.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertBookingCallEventRequest {
    /** Tên event Jitsi External API, vd videoConferenceJoined */
    private String eventType;
    private String roomName;
    /** ID phiên trình duyệt để ghép join/leave */
    private String clientSessionId;
    /** JSON string hoặc object serialize sẵn */
    private String payload;
}
