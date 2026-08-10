package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingCallEventRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
import com.willa.ai.backend.dto.request.ExpertRefundBankDetailsRequest;
import com.willa.ai.backend.dto.response.ExpertBookingCallEventResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallHistoryResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallSessionResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCheckoutResponse;
import com.willa.ai.backend.dto.response.ExpertBookingMessageResponse;
import com.willa.ai.backend.dto.response.ExpertBookingResponse;
import com.willa.ai.backend.dto.response.ExpertRefundSupportMessageResponse;

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

    /** Cron: hết SLA Accept → EXPIRED + REFUND_PENDING. */
    int expireUnacceptedBookings();

    /** Cron: hết Q&A REVIEW → auto COMPLETED. */
    int autoCompleteExpiredReviewQa();

    /** Cron: HOURLY hết phút call → auto COMPLETED. */
    int autoCompleteHourlyCallExhausted();

    /** Cron: HOURLY quá hạn dùng (30 ngày) → auto COMPLETED. */
    int autoCompleteExpiredHourlyValidity();

    ExpertBookingResponse rejectByExpert(String expertUserEmail, Long bookingId, String reason);

    List<ExpertBookingResponse> listRefundPendingForAdmin();

    ExpertBookingResponse getBookingForAdmin(Long bookingId);

    /** Admin CS: đưa đơn đã PAID vào hàng đợi hoàn PayOS. */
    ExpertBookingResponse adminRequestRefund(Long bookingId, String reason);

    ExpertBookingResponse markRefundSettled(Long bookingId);

    List<ExpertRefundSupportMessageResponse> listRefundSupportMessages(String userEmail, Long bookingId, boolean asAdmin);

    ExpertRefundSupportMessageResponse sendRefundSupportMessage(
            String userEmail, Long bookingId, String content, boolean asAdmin);

    ExpertBookingResponse saveRefundBankDetails(String clientEmail, Long bookingId, ExpertRefundBankDetailsRequest request);

    /** Client mua thêm phút call trên đơn chưa Complete — PayOS theo hourly rate. */
    ExpertBookingCheckoutResponse purchaseExtraCallMinutes(String clientEmail, Long bookingId, int minutes);

    /** PayOS webhook/confirm: cộng phút vào booking nếu payment là call top-up. */
    void applyPaidCallTopupIfAny(Long paymentId);
}
