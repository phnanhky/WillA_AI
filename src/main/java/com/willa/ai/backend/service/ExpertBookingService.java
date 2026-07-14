package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingCallEventRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
import com.willa.ai.backend.dto.response.ExpertBookingCallEventResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallHistoryResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallSessionResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCheckoutResponse;
import com.willa.ai.backend.dto.response.ExpertBookingMessageResponse;
import com.willa.ai.backend.dto.response.ExpertBookingResponse;

import java.util.List;

public interface ExpertBookingService {

    ExpertBookingCheckoutResponse createBooking(String clientEmail, CreateExpertBookingRequest request);

    ExpertBookingCheckoutResponse getCheckoutForClient(String clientEmail, Long bookingId);

    List<ExpertBookingResponse> listMyBookings(String clientEmail);

    List<ExpertBookingResponse> listAssignedBookings(String expertUserEmail);

    ExpertBookingResponse updateByExpert(String expertUserEmail, Long bookingId, ExpertBookingFeedbackRequest request);

    ExpertBookingResponse addMaterials(String clientEmail, Long bookingId, AddExpertBookingMaterialsRequest request);

    List<ExpertBookingMessageResponse> listMessages(String userEmail, Long bookingId);

    ExpertBookingMessageResponse sendMessage(String userEmail, Long bookingId, ExpertBookingMessageRequest request);

    ExpertBookingCallEventResponse recordCallEvent(
            String userEmail, Long bookingId, ExpertBookingCallEventRequest request);

    ExpertBookingCallHistoryResponse getCallHistory(String userEmail, Long bookingId);

    /** Admin: sessions + event chi tiết Jitsi. */
    ExpertBookingCallHistoryResponse getCallHistoryForAdmin(Long bookingId);

    List<ExpertBookingCallSessionResponse> listRecentCallSessionsForAdmin(int limit);
}
