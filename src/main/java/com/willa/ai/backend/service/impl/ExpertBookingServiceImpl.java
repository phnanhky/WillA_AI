package com.willa.ai.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingAttachmentRequest;
import com.willa.ai.backend.dto.request.ExpertBookingCallEventRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
import com.willa.ai.backend.dto.response.ExpertBookingAttachmentResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallEventResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallHistoryResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallSessionResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCheckoutResponse;
import com.willa.ai.backend.dto.response.ExpertBookingMessageResponse;
import com.willa.ai.backend.dto.response.ExpertBookingResponse;
import com.willa.ai.backend.entity.ExpertBooking;
import com.willa.ai.backend.entity.ExpertBookingAttachment;
import com.willa.ai.backend.entity.ExpertBookingCallEvent;
import com.willa.ai.backend.entity.ExpertBookingCallSession;
import com.willa.ai.backend.entity.ExpertBookingMessage;
import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkspaceExpert;
import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import com.willa.ai.backend.entity.enums.ExpertBookingType;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.repository.ExpertBookingAttachmentRepository;
import com.willa.ai.backend.repository.ExpertBookingCallEventRepository;
import com.willa.ai.backend.repository.ExpertBookingCallSessionRepository;
import com.willa.ai.backend.repository.ExpertBookingMessageRepository;
import com.willa.ai.backend.repository.ExpertBookingRepository;
import com.willa.ai.backend.repository.PaymentRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceExpertRepository;
import com.willa.ai.backend.service.EmailService;
import com.willa.ai.backend.service.ExpertBookingPolicy;
import com.willa.ai.backend.service.ExpertBookingRealtimeService;
import com.willa.ai.backend.service.ExpertBookingService;
import com.willa.ai.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.type.CheckoutResponseData;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpertBookingServiceImpl implements ExpertBookingService {

    private static final long MIN_PAYMENT = 1_000L;

    private final ExpertBookingRepository bookingRepository;
    private final ExpertBookingAttachmentRepository attachmentRepository;
    private final ExpertBookingMessageRepository messageRepository;
    private final ExpertBookingCallEventRepository callEventRepository;
    private final ExpertBookingCallSessionRepository callSessionRepository;
    private final WorkspaceExpertRepository expertRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ExpertBookingRealtimeService expertBookingRealtimeService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Override
    public ExpertBookingCheckoutResponse createBooking(String clientEmail, CreateExpertBookingRequest request) {
        BookingAmount amount = resolveBookingAmount(clientEmail, request);
        User client = amount.client();
        WorkspaceExpert expert = amount.expert();

        ExpertBooking booking = ExpertBooking.builder()
                .client(client)
                .expert(expert)
                .bookingType(amount.type())
                .status(ExpertBookingStatus.PENDING_PAYMENT)
                .brief(amount.brief())
                .publications(amount.publications())
                .driveLinks(serializeDriveLinks(amount.driveLinks()))
                .hourlyHours(amount.hours())
                .amountVnd(amount.amount())
                .parentBooking(amount.parent())
                .build();
        booking = bookingRepository.save(booking);
        saveAttachments(booking, request.getAttachments());

        Payment payment = createPaymentForBooking(booking);
        booking.setPayment(payment);
        booking = bookingRepository.save(booking);

        CheckoutResponseData checkout = paymentService.createCheckoutForPayment(payment);
        ExpertBookingResponse response = mapToResponse(booking);
        expertBookingRealtimeService.publishBookingUpdated(booking, response);
        return ExpertBookingCheckoutResponse.builder()
                .booking(response)
                .checkout(checkout)
                .build();
    }

    @Override
    public ExpertBookingCheckoutResponse getCheckoutForClient(String clientEmail, Long bookingId) {
        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));

        if (!booking.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Booking không thuộc về bạn");
        }
        if (booking.getStatus() != ExpertBookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("Booking không ở trạng thái chờ thanh toán");
        }

        Payment payment = booking.getPayment();
        if (payment == null) {
            throw new IllegalArgumentException("Chưa có thông tin thanh toán");
        }

        CheckoutResponseData checkout = paymentService.createCheckoutForPayment(payment);
        return ExpertBookingCheckoutResponse.builder()
                .booking(mapToResponse(booking))
                .checkout(checkout)
                .build();
    }

    @Override
    public ExpertBookingResponse addMaterials(
            String clientEmail, Long bookingId, AddExpertBookingMaterialsRequest request) {
        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));

        if (!booking.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Booking không thuộc về bạn");
        }
        if (booking.getStatus() == ExpertBookingStatus.CANCELLED
                || booking.getStatus() == ExpertBookingStatus.REJECTED
                || booking.getStatus() == ExpertBookingStatus.EXPIRED
                || booking.getStatus() == ExpertBookingStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    booking.getStatus() == ExpertBookingStatus.COMPLETED
                            ? "Booking đã hoàn tất — không thể thêm tài liệu"
                            : "Booking đã hủy, hết hạn hoặc bị từ chối");
        }

        List<String> newLinks = sanitizeDriveLinks(request.getDriveLinks());
        if (!newLinks.isEmpty()) {
            List<String> merged = new ArrayList<>(deserializeDriveLinks(booking.getDriveLinks()));
            merged.addAll(newLinks);
            booking.setDriveLinks(serializeDriveLinks(merged));
        }
        saveAttachments(booking, request.getAttachments());

        ExpertBooking saved = bookingRepository.save(booking);
        expertBookingRealtimeService.publishBookingUpdated(saved, mapToResponse(saved));
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpertBookingResponse> listMyBookings(String clientEmail) {
        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return bookingRepository.findByClientIdOrderByCreatedAtDesc(client.getId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpertBookingResponse> listAssignedBookings(String expertUserEmail) {
        User expertUser = userRepository.findByEmail(expertUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return bookingRepository.findByExpertUserIdOrderByCreatedAtDesc(expertUser.getId()).stream()
                .filter(b -> b.getStatus() != ExpertBookingStatus.CANCELLED)
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ExpertBookingResponse updateByExpert(
            String expertUserEmail, Long bookingId, ExpertBookingFeedbackRequest request) {
        ExpertBooking booking = loadBookingForExpert(expertUserEmail, bookingId);
        if (booking.getStatus() == ExpertBookingStatus.PENDING_PAYMENT
                || booking.getStatus() == ExpertBookingStatus.CANCELLED
                || booking.getStatus() == ExpertBookingStatus.REJECTED
                || booking.getStatus() == ExpertBookingStatus.EXPIRED) {
            throw new IllegalArgumentException("Booking chưa sẵn sàng để cập nhật");
        }

        if (request.getStatus() != null) {
            ExpertBookingStatus next = request.getStatus();
            if (next != ExpertBookingStatus.IN_PROGRESS && next != ExpertBookingStatus.COMPLETED) {
                throw new IllegalArgumentException("Trạng thái không hợp lệ");
            }
            if (next == ExpertBookingStatus.IN_PROGRESS
                    && booking.getStatus() == ExpertBookingStatus.AWAITING_EXPERT) {
                applyAccept(booking);
            } else {
                booking.setStatus(next);
            }
            if (next == ExpertBookingStatus.COMPLETED) {
                if (trimOrNull(request.getFeedback()) == null
                        && trimOrNull(booking.getExpertFeedback()) == null) {
                    throw new IllegalArgumentException(
                            "Cần nhập phản hồi trước khi hoàn tất hỗ trợ");
                }
                booking.setCompletedAt(LocalDateTime.now());
                booking.setMeetingRoomUrl(null);
            }
        }

        String feedback = trimOrNull(request.getFeedback());
        if (feedback != null) {
            booking.setExpertFeedback(feedback);
            markFeedbackDeliveredIfNeeded(booking);
        }

        if (booking.getStatus() == ExpertBookingStatus.AWAITING_EXPERT && feedback != null) {
            applyAccept(booking);
        }

        ensureMeetingRoomUrlPersisted(booking);
        ExpertBooking saved = bookingRepository.save(booking);
        ExpertBookingResponse response = mapToResponse(saved);
        expertBookingRealtimeService.publishBookingUpdated(saved, response);
        return response;
    }

    @Override
    public int expireUnacceptedBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<ExpertBooking> overdue = bookingRepository.findByStatusAndAcceptDeadlineAtBefore(
                ExpertBookingStatus.AWAITING_EXPERT, now);
        int count = 0;
        for (ExpertBooking booking : overdue) {
            try {
                expireAndRefund(booking,
                        "Expert không nhận đơn trong " + ExpertBookingPolicy.ACCEPT_SLA_HOURS
                                + " giờ — hệ thống hủy và yêu cầu hoàn tiền.",
                        ExpertBookingStatus.EXPIRED);
                count++;
            } catch (Exception e) {
                log.error("Failed to expire booking {}: {}", booking.getId(), e.getMessage());
            }
        }
        return count;
    }

    @Override
    public int autoCompleteExpiredReviewQa() {
        LocalDateTime now = LocalDateTime.now();
        List<ExpertBooking> expiredQa = bookingRepository.findByStatusAndBookingTypeAndQaEndsAtBefore(
                ExpertBookingStatus.IN_PROGRESS, ExpertBookingType.REVIEW, now);
        int count = 0;
        for (ExpertBooking booking : expiredQa) {
            if (trimOrNull(booking.getExpertFeedback()) == null) {
                continue;
            }
            booking.setStatus(ExpertBookingStatus.COMPLETED);
            booking.setCompletedAt(now);
            booking.setMeetingRoomUrl(null);
            ExpertBooking saved = bookingRepository.save(booking);
            expertBookingRealtimeService.publishBookingUpdated(saved, mapToResponse(saved));
            count++;
            log.info("Auto-completed REVIEW booking {} after Q&A window", booking.getId());
        }
        return count;
    }

    @Override
    public int autoCompleteHourlyCallExhausted() {
        List<ExpertBooking> hourly = bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == ExpertBookingStatus.IN_PROGRESS
                        && b.getBookingType() == ExpertBookingType.HOURLY)
                .toList();
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ExpertBooking booking : hourly) {
            if (hasCallQuotaRemaining(booking)) {
                continue;
            }
            boolean openSessions = !callSessionRepository.findByBookingIdAndLeftAtIsNull(booking.getId()).isEmpty();
            if (openSessions) {
                continue;
            }
            booking.setStatus(ExpertBookingStatus.COMPLETED);
            booking.setCompletedAt(now);
            booking.setMeetingRoomUrl(null);
            if (trimOrNull(booking.getExpertFeedback()) == null) {
                booking.setExpertFeedback("Phiên theo giờ đã hết phút call — hệ thống tự đóng.");
            }
            ExpertBooking saved = bookingRepository.save(booking);
            expertBookingRealtimeService.publishBookingUpdated(saved, mapToResponse(saved));
            count++;
            log.info("Auto-completed HOURLY booking {} after call quota exhausted", booking.getId());
        }
        return count;
    }

    @Override
    public ExpertBookingResponse rejectByExpert(String expertUserEmail, Long bookingId, String reason) {
        ExpertBooking booking = loadBookingForExpert(expertUserEmail, bookingId);
        if (booking.getStatus() != ExpertBookingStatus.AWAITING_EXPERT
                && booking.getStatus() != ExpertBookingStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Chỉ từ chối được đơn đang chờ nhận hoặc đang hỗ trợ");
        }
        if (booking.getStatus() == ExpertBookingStatus.IN_PROGRESS
                && trimOrNull(booking.getExpertFeedback()) != null) {
            throw new IllegalArgumentException("Đã gửi feedback — không thể từ chối, hãy đóng phiên");
        }
        String why = trimOrNull(reason);
        if (why == null) {
            why = "Expert từ chối đơn";
        }
        expireAndRefund(booking, why, ExpertBookingStatus.REJECTED);
        return mapToResponse(bookingRepository.findById(bookingId).orElse(booking));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpertBookingResponse> listRefundPendingForAdmin() {
        return bookingRepository.findAll().stream()
                .filter(b -> {
                    Payment p = b.getPayment();
                    return p != null && p.getStatus() == PaymentStatus.REFUND_PENDING;
                })
                .sorted(Comparator.comparing(ExpertBooking::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ExpertBookingResponse markRefundSettled(Long bookingId) {
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));
        Payment payment = booking.getPayment();
        if (payment == null || payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            throw new IllegalArgumentException("Booking không ở trạng thái chờ hoàn tiền ops");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        log.info("Refund settled for booking {} orderCode={}", bookingId, payment.getOrderCode());
        return mapToResponse(booking);
    }

    private void applyAccept(ExpertBooking booking) {
        LocalDateTime now = LocalDateTime.now();
        if (booking.getAcceptedAt() == null) {
            booking.setAcceptedAt(now);
        }
        if (booking.getCallMinutesLimit() == null || booking.getCallMinutesLimit() <= 0) {
            booking.setCallMinutesLimit(ExpertBookingPolicy.callMinutesFor(
                    booking.getBookingType(), booking.getHourlyHours()));
        }
        booking.setStatus(ExpertBookingStatus.IN_PROGRESS);
    }

    private void markFeedbackDeliveredIfNeeded(ExpertBooking booking) {
        if (booking.getBookingType() != ExpertBookingType.REVIEW) {
            return;
        }
        if (booking.getFeedbackDeliveredAt() != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        booking.setFeedbackDeliveredAt(now);
        booking.setQaEndsAt(now.plusHours(ExpertBookingPolicy.REVIEW_QA_HOURS));
    }

    private void expireAndRefund(ExpertBooking booking, String reason, ExpertBookingStatus terminalStatus) {
        booking.setStatus(terminalStatus);
        booking.setRejectReason(reason);
        booking.setMeetingRoomUrl(null);
        Payment payment = booking.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            paymentRepository.save(payment);
            log.warn(
                    "EXPERT_BOOKING_REFUND_PENDING: bookingId={} orderCode={} amount={} reason={} — ops hoàn trên PayOS dashboard rồi mark settled",
                    booking.getId(),
                    payment.getOrderCode(),
                    payment.getAmount(),
                    reason);
        }
        ExpertBooking saved = bookingRepository.save(booking);
        expertBookingRealtimeService.publishBookingUpdated(saved, mapToResponse(saved));
        notifyClientRefund(saved, reason);
    }

    private void notifyClientRefund(ExpertBooking booking, String reason) {
        try {
            User client = booking.getClient();
            if (client == null || client.getEmail() == null) {
                return;
            }
            emailService.sendSimpleEmail(
                    client.getEmail(),
                    "WillA — Đơn Expert đã hủy, đang xử lý hoàn tiền",
                    "Xin chào " + (client.getFullName() != null ? client.getFullName() : "")
                            + ",\n\nĐơn Expert #" + booking.getId() + " đã bị hủy.\n"
                            + reason
                            + "\n\nChúng tôi đã ghi nhận yêu cầu hoàn "
                            + booking.getAmountVnd()
                            + " VND. Tiền thường về tài khoản trong 1–3 ngày làm việc sau khi ops hoàn trên PayOS. "
                            + "Nếu quá hạn, liên hệ support@willaai.tech kèm mã PayOS "
                            + (booking.getPayment() != null ? booking.getPayment().getOrderCode() : "—")
                            + ".\n\n— WillA");
        } catch (Exception e) {
            log.warn("Could not email refund notice for booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpertBookingMessageResponse> listMessages(String userEmail, Long bookingId) {
        ExpertBooking booking = loadBookingForParticipant(userEmail, bookingId);
        assertChatReadable(booking);
        return messageRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()).stream()
                .map(this::mapMessage)
                .toList();
    }

    @Override
    public ExpertBookingMessageResponse sendMessage(
            String userEmail, Long bookingId, ExpertBookingMessageRequest request) {
        ExpertBooking booking = loadBookingForParticipant(userEmail, bookingId);
        User sender = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        assertChatWritable(booking, sender);

        String content = trimOrNull(request.getContent());
        if (content == null) {
            throw new IllegalArgumentException("Nội dung tin nhắn trống");
        }

        ExpertBookingMessage message = ExpertBookingMessage.builder()
                .booking(booking)
                .sender(sender)
                .content(content)
                .build();
        message = messageRepository.save(message);
        ExpertBookingMessageResponse response = mapMessage(message);
        expertBookingRealtimeService.publishMessageCreated(booking.getId(), response);
        return response;
    }

    @Override
    public ExpertBookingCallEventResponse recordCallEvent(
            String userEmail, Long bookingId, ExpertBookingCallEventRequest request) {
        ExpertBooking booking = loadBookingForParticipant(userEmail, bookingId);
        assertCallActive(booking);

        String eventType = trimOrNull(request.getEventType());
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is required");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String normalized = eventType.toLowerCase(Locale.ROOT);
        boolean leaveLike = normalized.contains("videoconferenceleft")
                || normalized.contains("readytoclose")
                || normalized.contains("iframedisposed")
                || "left".equals(normalized);
        assertCallEventAllowed(booking, leaveLike);

        String roomName = trimOrNull(request.getRoomName());
        if (roomName == null) {
            String url = trimOrNull(booking.getMeetingRoomUrl());
            roomName = url != null && url.contains("meet.jit.si/")
                    ? url.substring(url.lastIndexOf('/') + 1)
                    : ("WillaEB" + booking.getId() + "x" + generateRoomSecret());
        }
        String clientSessionId = trimOrNull(request.getClientSessionId());
        String payload = trimOrNull(request.getPayload());
        if (payload != null && payload.length() > 8000) {
            payload = payload.substring(0, 8000);
        }

        ExpertBookingCallEvent event = callEventRepository.save(ExpertBookingCallEvent.builder()
                .booking(booking)
                .user(user)
                .eventType(eventType)
                .roomName(roomName)
                .clientSessionId(clientSessionId)
                .payload(payload)
                .build());

        String normalizedType = eventType.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("videoconferencejoined") || "joined".equals(normalizedType)) {
            openCallSession(booking, user, roomName, clientSessionId);
        } else if (normalizedType.contains("videoconferenceleft")
                || normalizedType.contains("readytoclose")
                || normalizedType.contains("iframedisposed")
                || "left".equals(normalizedType)) {
            closeCallSession(booking.getId(), clientSessionId);
        }

        return mapCallEvent(event);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpertBookingCallHistoryResponse getCallHistory(String userEmail, Long bookingId) {
        ExpertBooking booking = loadBookingForParticipant(userEmail, bookingId);
        // Participant: chỉ phiên + tổng thời gian — không lộ event chi tiết Jitsi
        return buildCallHistory(booking.getId(), false);
    }

    @Transactional(readOnly = true)
    public ExpertBookingCallHistoryResponse getCallHistoryForAdmin(Long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new IllegalArgumentException("Booking không tồn tại");
        }
        return buildCallHistory(bookingId, true);
    }

    private ExpertBookingCallHistoryResponse buildCallHistory(Long bookingId, boolean includeEvents) {
        List<ExpertBookingCallSessionResponse> sessions = callSessionRepository
                .findByBookingIdOrderByJoinedAtDesc(bookingId)
                .stream()
                .map(this::mapCallSession)
                .toList();
        long totalDuration = sessions.stream()
                .map(ExpertBookingCallSessionResponse::getDurationSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        List<ExpertBookingCallEventResponse> events = includeEvents
                ? callEventRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                        .map(this::mapCallEvent)
                        .toList()
                : List.of();
        return ExpertBookingCallHistoryResponse.builder()
                .totalDurationSeconds(totalDuration)
                .sessionCount(sessions.size())
                .sessions(sessions)
                .events(events)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpertBookingCallSessionResponse> listRecentCallSessionsForAdmin(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return callSessionRepository.findTop100ByOrderByJoinedAtDesc().stream()
                .limit(safeLimit)
                .map(this::mapCallSession)
                .toList();
    }

    private void assertCallEventAllowed(ExpertBooking booking, boolean leaveLike) {
        ExpertBookingStatus status = booking.getStatus();
        if (status == ExpertBookingStatus.IN_PROGRESS) {
            if (!leaveLike && !hasCallQuotaRemaining(booking)) {
                throw new IllegalArgumentException(
                        "Đã hết phút call của gói. Book thêm gói theo giờ để tiếp tục.");
            }
            return;
        }
        // Cho phép ghi leave sau khi đơn vừa hoàn tất (race hangup vs complete)
        if (leaveLike && status == ExpertBookingStatus.COMPLETED) {
            return;
        }
        throw new IllegalArgumentException(
                status == ExpertBookingStatus.AWAITING_EXPERT
                        ? "Expert chưa nhận đơn — chưa mở video call"
                        : "Video call không khả dụng ở trạng thái hiện tại");
    }

    private void assertCallActive(ExpertBooking booking) {
        assertCallEventAllowed(booking, false);
    }

    private void openCallSession(ExpertBooking booking, User user, String roomName, String clientSessionId) {
        if (clientSessionId != null
                && callSessionRepository
                .findFirstByBookingIdAndClientSessionIdAndLeftAtIsNullOrderByJoinedAtDesc(
                        booking.getId(), clientSessionId)
                .isPresent()) {
            return;
        }
        callSessionRepository.save(ExpertBookingCallSession.builder()
                .booking(booking)
                .user(user)
                .roomName(roomName)
                .clientSessionId(clientSessionId)
                .joinedAt(LocalDateTime.now())
                .build());
    }

    private void closeCallSession(Long bookingId, String clientSessionId) {
        if (clientSessionId == null) {
            return;
        }
        callSessionRepository
                .findFirstByBookingIdAndClientSessionIdAndLeftAtIsNullOrderByJoinedAtDesc(bookingId, clientSessionId)
                .ifPresent(session -> {
                    LocalDateTime leftAt = LocalDateTime.now();
                    session.setLeftAt(leftAt);
                    if (session.getJoinedAt() != null) {
                        session.setDurationSeconds(
                                Math.max(0, Duration.between(session.getJoinedAt(), leftAt).getSeconds()));
                    }
                    callSessionRepository.save(session);
                });
    }

    private ExpertBookingCallEventResponse mapCallEvent(ExpertBookingCallEvent event) {
        User u = event.getUser();
        return ExpertBookingCallEventResponse.builder()
                .id(event.getId())
                .bookingId(event.getBooking().getId())
                .userId(u.getId())
                .userEmail(u.getEmail())
                .userName(u.getFullName())
                .eventType(event.getEventType())
                .roomName(event.getRoomName())
                .clientSessionId(event.getClientSessionId())
                .payload(event.getPayload())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private ExpertBookingCallSessionResponse mapCallSession(ExpertBookingCallSession session) {
        User u = session.getUser();
        return ExpertBookingCallSessionResponse.builder()
                .id(session.getId())
                .bookingId(session.getBooking().getId())
                .userId(u.getId())
                .userEmail(u.getEmail())
                .userName(u.getFullName())
                .roomName(session.getRoomName())
                .clientSessionId(session.getClientSessionId())
                .joinedAt(session.getJoinedAt())
                .leftAt(session.getLeftAt())
                .durationSeconds(session.getDurationSeconds())
                .build();
    }

    private ExpertBooking loadBookingForExpert(String expertUserEmail, Long bookingId) {
        User expertUser = userRepository.findByEmail(expertUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));
        if (!booking.getExpert().getUser().getId().equals(expertUser.getId())) {
            throw new IllegalArgumentException("Bạn không phải expert của booking này");
        }
        return booking;
    }

    private ExpertBooking loadBookingForParticipant(String userEmail, Long bookingId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));
        boolean isClient = booking.getClient().getId().equals(user.getId());
        boolean isExpert = booking.getExpert().getUser().getId().equals(user.getId());
        if (!isClient && !isExpert) {
            throw new IllegalArgumentException("Bạn không có quyền truy cập booking này");
        }
        return booking;
    }

    private void assertChatReadable(ExpertBooking booking) {
        ExpertBookingStatus status = booking.getStatus();
        if (status == ExpertBookingStatus.CANCELLED
                || status == ExpertBookingStatus.REJECTED
                || status == ExpertBookingStatus.EXPIRED) {
            throw new IllegalArgumentException("Chat chưa khả dụng cho booking này");
        }
    }

    private void assertChatWritable(ExpertBooking booking, User sender) {
        ExpertBookingStatus status = booking.getStatus();
        if (status != ExpertBookingStatus.AWAITING_EXPERT
                && status != ExpertBookingStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Không thể gửi tin nhắn ở trạng thái hiện tại");
        }
        boolean isClient = booking.getClient().getId().equals(sender.getId());
        if (!isClient) {
            return; // expert luôn gửi được khi phiên mở
        }
        if (!clientCanSendMessage(booking)) {
            throw new IllegalArgumentException(
                    "Đã hết hạn Q&A hoặc hết 8 tin hỏi làm rõ. "
                            + "Book gói theo giờ nếu cần trao đổi thêm.");
        }
    }

    private boolean clientCanSendMessage(ExpertBooking booking) {
        if (booking.getStatus() != ExpertBookingStatus.AWAITING_EXPERT
                && booking.getStatus() != ExpertBookingStatus.IN_PROGRESS) {
            return false;
        }
        // Trước khi có feedback chính: client vẫn nhắn được (làm rõ brief)
        if (booking.getBookingType() != ExpertBookingType.REVIEW
                || booking.getFeedbackDeliveredAt() == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        if (booking.getQaEndsAt() != null && now.isAfter(booking.getQaEndsAt())) {
            return false;
        }
        return countClientQaMessages(booking) < ExpertBookingPolicy.REVIEW_QA_CLIENT_MESSAGES;
    }

    private int countClientQaMessages(ExpertBooking booking) {
        if (booking.getFeedbackDeliveredAt() == null) {
            return 0;
        }
        return (int) messageRepository.countByBookingIdAndSenderIdAndCreatedAtAfter(
                booking.getId(),
                booking.getClient().getId(),
                booking.getFeedbackDeliveredAt());
    }

    private long computeCallSecondsUsed(ExpertBooking booking) {
        long closed = callSessionRepository.findByBookingIdOrderByJoinedAtDesc(booking.getId()).stream()
                .filter(s -> s.getLeftAt() != null)
                .map(ExpertBookingCallSession::getDurationSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long open = callSessionRepository.findByBookingIdAndLeftAtIsNull(booking.getId()).stream()
                .mapToLong(s -> {
                    if (s.getJoinedAt() == null) return 0L;
                    return Math.max(0, Duration.between(s.getJoinedAt(), LocalDateTime.now()).getSeconds());
                })
                .sum();
        return closed + open;
    }

    private boolean hasCallQuotaRemaining(ExpertBooking booking) {
        int limitMin = booking.getCallMinutesLimit() != null && booking.getCallMinutesLimit() > 0
                ? booking.getCallMinutesLimit()
                : ExpertBookingPolicy.callMinutesFor(booking.getBookingType(), booking.getHourlyHours());
        long used = computeCallSecondsUsed(booking);
        return used < limitMin * 60L;
    }

    private String buildQuotaHint(ExpertBooking booking) {
        if (booking.getStatus() == ExpertBookingStatus.AWAITING_EXPERT) {
            return "Expert cần nhận đơn trong 24h sau thanh toán; quá hạn sẽ hoàn tiền.";
        }
        if (booking.getStatus() != ExpertBookingStatus.IN_PROGRESS) {
            return null;
        }
        if (booking.getBookingType() == ExpertBookingType.REVIEW) {
            if (booking.getFeedbackDeliveredAt() == null) {
                return "Review gồm 1 nhận xét viết + tối đa 8 tin làm rõ trong 48h sau feedback; call tối đa 15 phút.";
            }
            int used = countClientQaMessages(booking);
            int left = Math.max(0, ExpertBookingPolicy.REVIEW_QA_CLIENT_MESSAGES - used);
            return "Q&A: còn " + left + "/" + ExpertBookingPolicy.REVIEW_QA_CLIENT_MESSAGES
                    + " tin · hết hạn "
                    + (booking.getQaEndsAt() != null ? booking.getQaEndsAt().toString() : "—");
        }
        int limit = booking.getCallMinutesLimit() != null
                ? booking.getCallMinutesLimit()
                : ExpertBookingPolicy.callMinutesForHourly(booking.getHourlyHours());
        long remSec = Math.max(0, limit * 60L - computeCallSecondsUsed(booking));
        return "Call còn ~" + (remSec / 60) + " phút (gói " + limit + " phút).";
    }

    private Payment createPaymentForBooking(ExpertBooking booking) {
        long amount = booking.getAmountVnd();
        if (amount < MIN_PAYMENT) {
            throw new IllegalArgumentException("Số tiền thanh toán tối thiểu là 1.000 VND");
        }

        Long orderCode = System.currentTimeMillis() / 1000;
        String description = booking.getBookingType() == ExpertBookingType.REVIEW
                ? "WillA Expert Review"
                : "WillA Expert Hourly";

        Payment payment = Payment.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .status(PaymentStatus.PENDING)
                .user(booking.getClient())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return paymentRepository.save(payment);
    }

    private record BookingAmount(
            User client,
            WorkspaceExpert expert,
            ExpertBookingType type,
            String brief,
            String publications,
            List<String> driveLinks,
            Integer hours,
            long amount,
            ExpertBooking parent
    ) {}

    private BookingAmount resolveBookingAmount(String clientEmail, CreateExpertBookingRequest request) {
        if (request.getExpertId() == null) {
            throw new IllegalArgumentException("Thiếu expertId");
        }
        if (request.getBookingType() == null) {
            throw new IllegalArgumentException("Thiếu loại booking");
        }
        String brief = trimOrNull(request.getBrief());
        if (brief == null) {
            throw new IllegalArgumentException("Vui lòng mô tả yêu cầu của bạn");
        }

        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        WorkspaceExpert expert = expertRepository.findById(request.getExpertId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy expert"));
        if (!Boolean.TRUE.equals(expert.getIsActive())) {
            throw new IllegalArgumentException("Expert này hiện không nhận booking");
        }
        if (expert.getUser().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Bạn không thể book chính mình");
        }

        ExpertBookingType type = request.getBookingType();
        long amount;
        Integer hours = null;
        ExpertBooking parent = null;

        if (type == ExpertBookingType.REVIEW) {
            Long reviewPrice = expert.getReviewPrice();
            if (reviewPrice == null || reviewPrice <= 0) {
                throw new IllegalArgumentException("Expert này chưa mở dịch vụ review ấn phẩm");
            }
            amount = reviewPrice;
        } else if (type == ExpertBookingType.HOURLY) {
            Long hourlyRate = expert.getHourlyRate();
            if (hourlyRate == null || hourlyRate <= 0) {
                throw new IllegalArgumentException("Expert này chưa mở dịch vụ trao đổi theo giờ");
            }
            int h = request.getHours() != null ? request.getHours() : 1;
            if (h < 1 || h > 24) {
                throw new IllegalArgumentException("Số giờ phải từ 1 đến 24");
            }
            hours = h;
            amount = hourlyRate * h;
            if (request.getParentBookingId() != null) {
                parent = bookingRepository.findById(request.getParentBookingId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy booking gốc"));
                if (!parent.getClient().getId().equals(client.getId())) {
                    throw new IllegalArgumentException("Booking gốc không thuộc về bạn");
                }
                if (!parent.getExpert().getId().equals(expert.getId())) {
                    throw new IllegalArgumentException("Booking gốc không cùng expert");
                }
            }
        } else {
            throw new IllegalArgumentException("Loại booking không hợp lệ");
        }

        if (amount < MIN_PAYMENT) {
            throw new IllegalArgumentException("Số tiền thanh toán tối thiểu là 1.000 VND");
        }

        return new BookingAmount(
                client,
                expert,
                type,
                brief,
                trimOrNull(request.getPublications()),
                sanitizeDriveLinks(request.getDriveLinks()),
                hours,
                amount,
                parent
        );
    }

    private void saveAttachments(ExpertBooking booking, List<ExpertBookingAttachmentRequest> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ExpertBookingAttachmentRequest item : items) {
            if (item == null) continue;
            String fileUrl = trimOrNull(item.getFileUrl());
            String fileName = trimOrNull(item.getFileName());
            if (fileUrl == null || fileName == null) continue;
            validateUploadedFileUrl(fileUrl);
            ExpertBookingAttachment att = ExpertBookingAttachment.builder()
                    .booking(booking)
                    .fileName(fileName)
                    .fileUrl(fileUrl)
                    .fileSizeBytes(item.getFileSizeBytes())
                    .contentType(trimOrNull(item.getContentType()))
                    .build();
            booking.getAttachments().add(att);
            attachmentRepository.save(att);
        }
    }

    private void validateUploadedFileUrl(String fileUrl) {
        String lower = fileUrl.toLowerCase(Locale.ROOT);
        if (!lower.contains("/api/files/download/")) {
            throw new IllegalArgumentException("URL file không hợp lệ — vui lòng upload qua Willa");
        }
    }

    private List<String> sanitizeDriveLinks(List<String> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String link : links) {
            String trimmed = trimOrNull(link);
            if (trimmed == null) continue;
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                throw new IllegalArgumentException("Link phải bắt đầu bằng http:// hoặc https://");
            }
            out.add(trimmed);
        }
        return new ArrayList<>(out);
    }

    private String serializeDriveLinks(List<String> links) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(links);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize drive links", e);
        }
    }

    private List<String> deserializeDriveLinks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private ExpertBookingResponse mapToResponse(ExpertBooking booking) {
        User client = booking.getClient();
        WorkspaceExpert expert = booking.getExpert();
        User expertUser = expert.getUser();
        Payment payment = booking.getPayment();

        List<ExpertBookingAttachment> atts = booking.getAttachments();
        if (atts == null || atts.isEmpty()) {
            atts = attachmentRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId());
        }

        long callUsed = computeCallSecondsUsed(booking);
        int callLimitMin = booking.getCallMinutesLimit() != null && booking.getCallMinutesLimit() > 0
                ? booking.getCallMinutesLimit()
                : ExpertBookingPolicy.callMinutesFor(booking.getBookingType(), booking.getHourlyHours());
        long callRemaining = Math.max(0, callLimitMin * 60L - callUsed);
        boolean canCall = booking.getStatus() == ExpertBookingStatus.IN_PROGRESS && callRemaining > 0;
        int qaUsed = countClientQaMessages(booking);

        return ExpertBookingResponse.builder()
                .id(booking.getId())
                .bookingType(booking.getBookingType())
                .status(booking.getStatus())
                .brief(booking.getBrief())
                .publications(booking.getPublications())
                .driveLinks(deserializeDriveLinks(booking.getDriveLinks()))
                .attachments(atts.stream().map(this::mapAttachment).toList())
                .expertFeedback(booking.getExpertFeedback())
                .hourlyHours(booking.getHourlyHours())
                .amountVnd(booking.getAmountVnd())
                .parentBookingId(booking.getParentBooking() != null ? booking.getParentBooking().getId() : null)
                .expertId(expert.getId())
                .expertName(expertUser.getFullName())
                .expertEmail(expertUser.getEmail())
                .clientName(client.getFullName())
                .clientEmail(client.getEmail())
                .orderCode(payment != null ? payment.getOrderCode() : null)
                .paymentStatus(payment != null && payment.getStatus() != null ? payment.getStatus().name() : null)
                .meetingRoomUrl(resolveMeetingRoomUrl(booking))
                .rejectReason(booking.getRejectReason())
                .createdAt(booking.getCreatedAt())
                .completedAt(booking.getCompletedAt())
                .paidAt(booking.getPaidAt())
                .acceptDeadlineAt(booking.getAcceptDeadlineAt())
                .acceptedAt(booking.getAcceptedAt())
                .feedbackDeliveredAt(booking.getFeedbackDeliveredAt())
                .qaEndsAt(booking.getQaEndsAt())
                .callMinutesLimit(callLimitMin)
                .clientQaMessagesUsed(qaUsed)
                .clientQaMessageLimit(ExpertBookingPolicy.REVIEW_QA_CLIENT_MESSAGES)
                .clientCanSendMessage(clientCanSendMessage(booking))
                .callSecondsUsed(callUsed)
                .callSecondsRemaining(callRemaining)
                .canCall(canCall)
                .quotaHint(buildQuotaHint(booking))
                .build();
    }

    /** Link Jitsi chỉ khi IN_PROGRESS và còn phút call. */
    private String resolveMeetingRoomUrl(ExpertBooking booking) {
        if (booking.getStatus() != ExpertBookingStatus.IN_PROGRESS) {
            return null;
        }
        if (!hasCallQuotaRemaining(booking)) {
            return null;
        }
        String url = trimOrNull(booking.getMeetingRoomUrl());
        if (url != null) {
            return url;
        }
        return buildMeetingRoomUrl(booking.getId(), generateRoomSecret());
    }

    private void ensureMeetingRoomUrlPersisted(ExpertBooking booking) {
        if (trimOrNull(booking.getMeetingRoomUrl()) != null) {
            return;
        }
        if (booking.getStatus() == ExpertBookingStatus.IN_PROGRESS) {
            booking.setMeetingRoomUrl(buildMeetingRoomUrl(booking.getId(), generateRoomSecret()));
        }
    }

    private ExpertBookingAttachmentResponse mapAttachment(ExpertBookingAttachment att) {
        return ExpertBookingAttachmentResponse.builder()
                .id(att.getId())
                .fileName(att.getFileName())
                .fileUrl(att.getFileUrl())
                .fileSizeBytes(att.getFileSizeBytes())
                .contentType(att.getContentType())
                .createdAt(att.getCreatedAt())
                .build();
    }

    private ExpertBookingMessageResponse mapMessage(ExpertBookingMessage message) {
        User sender = message.getSender();
        return ExpertBookingMessageResponse.builder()
                .id(message.getId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .senderEmail(sender.getEmail())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String generateRoomSecret() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /** Room không đoán được từ booking id — secret lưu trong URL. */
    public static String buildMeetingRoomUrl(Long bookingId, String roomSecret) {
        String secret = roomSecret != null && !roomSecret.isBlank()
                ? roomSecret.trim()
                : generateRoomSecret();
        return "https://meet.jit.si/WillaEB" + bookingId + "x" + secret;
    }

    /** @deprecated dùng buildMeetingRoomUrl(id, secret) */
    public static String buildMeetingRoomUrl(Long bookingId) {
        return buildMeetingRoomUrl(bookingId, generateRoomSecret());
    }
}
