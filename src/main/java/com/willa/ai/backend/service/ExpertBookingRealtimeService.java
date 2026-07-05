package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.ExpertBookingMessageResponse;
import com.willa.ai.backend.dto.response.ExpertBookingResponse;
import com.willa.ai.backend.entity.ExpertBooking;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpertBookingRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishMessageCreated(Long bookingId, ExpertBookingMessageResponse message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MESSAGE_CREATED");
        payload.put("bookingId", bookingId);
        payload.put("message", message);
        messagingTemplate.convertAndSend("/topic/expert-booking/" + bookingId, payload);
    }

    public void publishBookingUpdated(ExpertBooking booking, ExpertBookingResponse response) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "BOOKING_UPDATED");
        payload.put("bookingId", booking.getId());
        payload.put("booking", response);
        messagingTemplate.convertAndSend("/topic/expert-booking/" + booking.getId(), payload);
        notifyParticipants(booking.getId(), booking.getClient().getId(), booking.getExpert().getUser().getId());
    }

    public void notifyBookingsChanged(ExpertBooking booking) {
        notifyParticipants(
                booking.getId(),
                booking.getClient().getId(),
                booking.getExpert().getUser().getId());
    }

    private void notifyParticipants(Long bookingId, Long clientUserId, Long expertUserId) {
        Map<String, Object> payload = Map.of(
                "type", "BOOKINGS_CHANGED",
                "bookingId", bookingId);
        messagingTemplate.convertAndSend("/topic/expert-user/" + clientUserId, payload);
        messagingTemplate.convertAndSend("/topic/expert-user/" + expertUserId, payload);
    }
}
