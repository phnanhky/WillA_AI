package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
import com.willa.ai.backend.dto.response.ExpertBookingCheckoutResponse;
import com.willa.ai.backend.dto.response.ExpertBookingMessageResponse;
import com.willa.ai.backend.dto.response.ExpertBookingResponse;

import java.util.List;

public interface ExpertBookingService {

    ExpertBookingCheckoutResponse createBooking(String clientEmail, CreateExpertBookingRequest request);

    ExpertBookingCheckoutResponse getCheckoutForClient(String clientEmail, Long bookingId);

    ExpertBookingResponse acceptByExpert(String expertUserEmail, Long bookingId);

    ExpertBookingResponse rejectByExpert(String expertUserEmail, Long bookingId, String reason);

    List<ExpertBookingResponse> listMyBookings(String clientEmail);

    List<ExpertBookingResponse> listAssignedBookings(String expertUserEmail);

    ExpertBookingResponse updateByExpert(String expertUserEmail, Long bookingId, ExpertBookingFeedbackRequest request);

    ExpertBookingResponse addMaterials(String clientEmail, Long bookingId, AddExpertBookingMaterialsRequest request);

    List<ExpertBookingMessageResponse> listMessages(String userEmail, Long bookingId);

    ExpertBookingMessageResponse sendMessage(String userEmail, Long bookingId, ExpertBookingMessageRequest request);
}
