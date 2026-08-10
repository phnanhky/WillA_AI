package com.willa.ai.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.AddExpertCallMinutesRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingAttachmentRequest;
import com.willa.ai.backend.dto.request.ExpertBookingCallEventRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
import com.willa.ai.backend.dto.request.ExpertRefundBankDetailsRequest;
import com.willa.ai.backend.dto.response.ExpertBookingAttachmentResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallEventResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallHistoryResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCallSessionResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCheckoutResponse;
import com.willa.ai.backend.dto.response.ExpertBookingMessageResponse;
import com.willa.ai.backend.dto.response.ExpertBookingResponse;
import com.willa.ai.backend.dto.response.ExpertRefundSupportMessageResponse;
import com.willa.ai.backend.entity.ExpertBooking;
import com.willa.ai.backend.entity.ExpertBookingAttachment;
import com.willa.ai.backend.entity.ExpertBookingCallEvent;
import com.willa.ai.backend.entity.ExpertBookingCallSession;
import com.willa.ai.backend.entity.ExpertBookingCallTopup;
import com.willa.ai.backend.entity.ExpertBookingMessage;
import com.willa.ai.backend.entity.ExpertRefundSupportMessage;
import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkspaceExpert;
import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import com.willa.ai.backend.entity.enums.ExpertBookingType;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.entity.enums.Role;
import com.willa.ai.backend.repository.ExpertBookingAttachmentRepository;
import com.willa.ai.backend.repository.ExpertBookingCallEventRepository;
import com.willa.ai.backend.repository.ExpertBookingCallSessionRepository;
import com.willa.ai.backend.repository.ExpertBookingCallTopupRepository;
import com.willa.ai.backend.repository.ExpertBookingMessageRepository;
import com.willa.ai.backend.repository.ExpertBookingRepository;
import com.willa.ai.backend.repository.ExpertRefundSupportMessageRepository;
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
    private final ExpertRefundSupportMessageRepository refundSupportMessageRepository;
    private final ExpertBookingCallEventRepository callEventRepository;
    private final ExpertBookingCallSessionRepository callSessionRepository;
    private final ExpertBookingCallTopupRepository callTopupRepository;
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
    public ExpertBookingCheckoutResponse purchaseExtraCallMinutes(String clientEmail, Long bookingId, int minutes) {
        if (minutes < 1 || minutes > 480) {
            throw new IllegalArgumentException("Số phút phải từ 1 đến 480");
        }
        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));
        if (!booking.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Booking không thuộc về bạn");
        }
        ExpertBookingStatus st = booking.getStatus();
        if (st != ExpertBookingStatus.IN_PROGRESS && st != ExpertBookingStatus.AWAITING_EXPERT) {
            throw new IllegalArgumentException("Chỉ mua thêm phút khi đơn đang chờ Accept hoặc đang hỗ trợ");
        }
        WorkspaceExpert expert = booking.getExpert();
        Long hourlyRate = expert.getHourlyRate();
        if (hourlyRate == null || hourlyRate <= 0) {
            throw new IllegalArgumentException("Expert này chưa đặt giá theo giờ — không mua thêm phút được");
        }
        // Giá = ceil(hourlyRate * minutes / 60)
        long amount = (hourlyRate * minutes + 59L) / 60L;
        if (amount < MIN_PAYMENT) {
            throw new IllegalArgumentException(
                    "Số tiền tối thiểu 1.000 VND — hãy tăng số phút (rate "
                            + hourlyRate + " VND/giờ)");
        }

        Long orderCode = System.currentTimeMillis() / 1000;
        Payment payment = paymentRepository.save(Payment.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description("WillA Expert +" + minutes + " phút call")
                .status(PaymentStatus.PENDING)
                .user(client)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        callTopupRepository.save(ExpertBookingCallTopup.builder()
                .booking(booking)
                .payment(payment)
                .minutes(minutes)
                .amountVnd(amount)
                .status("PENDING")
                .build());

        CheckoutResponseData checkout = paymentService.createCheckoutForPayment(payment);
        return ExpertBookingCheckoutResponse.builder()
                .booking(mapToResponse(booking))
                .checkout(checkout)
                .build();
    }

    @Override
    public void applyPaidCallTopupIfAny(Long paymentId) {
        if (paymentId == null) {
            return;
        }
        callTopupRepository.findByPaymentId(paymentId).ifPresent(topup -> {
            if ("PAID".equalsIgnoreCase(topup.getStatus())) {
                return;
            }
            ExpertBooking booking = topup.getBooking();
            int currentLimit = booking.getCallMinutesLimit() != null && booking.getCallMinutesLimit() > 0
                    ? booking.getCallMinutesLimit()
                    : ExpertBookingPolicy.callMinutesFor(booking.getBookingType(), booking.getHourlyHours());
            booking.setCallMinutesLimit(currentLimit + topup.getMinutes());
            bookingRepository.save(booking);
            topup.setStatus("PAID");
            topup.setPaidAt(LocalDateTime.now());
            callTopupRepository.save(topup);
            ExpertBookingResponse response = mapToResponse(booking);
            expertBookingRealtimeService.publishBookingUpdated(booking, response);
            log.info(
                    "Applied call top-up: bookingId={} +{} min → limit={} paymentId={}",
                    booking.getId(),
                    topup.getMinutes(),
                    booking.getCallMinutesLimit(),
                    paymentId);
        });
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
    public int autoCompleteExpiredHourlyValidity() {
        LocalDateTime now = LocalDateTime.now();
        List<ExpertBooking> expired = bookingRepository.findByStatusAndBookingTypeAndServiceExpiresAtBefore(
                ExpertBookingStatus.IN_PROGRESS, ExpertBookingType.HOURLY, now);
        // Cũng đóng đơn Hourly còn AWAITING_EXPERT quá hạn dùng (hiếm: Accept SLA 24h < 30 ngày,
        // nhưng backfill / edge case).
        List<ExpertBooking> awaitingExpired = bookingRepository.findByStatusAndBookingTypeAndServiceExpiresAtBefore(
                ExpertBookingStatus.AWAITING_EXPERT, ExpertBookingType.HOURLY, now);
        int count = 0;
        for (ExpertBooking booking : expired) {
            count += completeHourlyValidityExpired(booking, now);
        }
        for (ExpertBooking booking : awaitingExpired) {
            // Quá hạn dùng mà chưa Accept → hết giá trị gói; hoàn tiền (chưa nhận việc).
            try {
                expireAndRefund(booking,
                        "Gói theo giờ hết hạn dùng (" + ExpertBookingPolicy.HOURLY_VALIDITY_DAYS
                                + " ngày) trước khi expert nhận đơn — yêu cầu hoàn tiền.",
                        ExpertBookingStatus.EXPIRED);
                count++;
            } catch (Exception e) {
                log.error("Failed to expire overdue hourly awaiting {}: {}", booking.getId(), e.getMessage());
            }
        }
        return count;
    }

    private int completeHourlyValidityExpired(ExpertBooking booking, LocalDateTime now) {
        boolean openSessions = !callSessionRepository.findByBookingIdAndLeftAtIsNull(booking.getId()).isEmpty();
        if (openSessions) {
            return 0;
        }
        booking.setStatus(ExpertBookingStatus.COMPLETED);
        booking.setCompletedAt(now);
        booking.setMeetingRoomUrl(null);
        if (trimOrNull(booking.getExpertFeedback()) == null) {
            booking.setExpertFeedback(
                    "Gói theo giờ hết hạn dùng (" + ExpertBookingPolicy.HOURLY_VALIDITY_DAYS
                            + " ngày kể từ thanh toán) — hệ thống tự đóng. Phút call còn lại không hoàn.");
        }
        ExpertBooking saved = bookingRepository.save(booking);
        expertBookingRealtimeService.publishBookingUpdated(saved, mapToResponse(saved));
        log.info("Auto-completed HOURLY booking {} after service validity expired", booking.getId());
        return 1;
    }

    @Override
    public ExpertBookingResponse rejectByExpert(String expertUserEmail, Long bookingId, String reason) {
        ExpertBooking booking = loadBookingForExpert(expertUserEmail, bookingId);
        if (booking.getStatus() != ExpertBookingStatus.AWAITING_EXPERT) {
            throw new IllegalArgumentException(
                    "Chỉ từ chối được khi đang chờ Accept. Sau khi nhận đơn hãy đóng phiên hoặc nhờ Admin xử lý.");
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
    @Transactional(readOnly = true)
    public ExpertBookingResponse getBookingForAdmin(Long bookingId) {
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking #" + bookingId));
        return mapToResponse(booking);
    }

    @Override
    public ExpertBookingResponse adminRequestRefund(Long bookingId, String reason) {
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking #" + bookingId));
        Payment payment = booking.getPayment();
        if (payment == null) {
            throw new IllegalArgumentException("Booking chưa có payment — không hoàn được");
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalArgumentException("Payment đã REFUNDED rồi");
        }
        if (payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            return mapToResponse(booking);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalArgumentException(
                    "Chỉ hoàn đơn đã PAID. Hiện tại: " + payment.getStatus());
        }

        String why = trimOrNull(reason);
        if (why == null) {
            why = "Admin hỗ trợ hoàn tiền khách";
        } else if (!why.toLowerCase().startsWith("admin")) {
            why = "Admin CS: " + why;
        }

        payment.setStatus(PaymentStatus.REFUND_PENDING);
        paymentRepository.save(payment);

        // Đóng phiên hỗ trợ nếu còn mở — giữ COMPLETED/EXPIRED/REJECTED như cũ
        ExpertBookingStatus st = booking.getStatus();
        if (st == ExpertBookingStatus.AWAITING_EXPERT
                || st == ExpertBookingStatus.IN_PROGRESS
                || st == ExpertBookingStatus.PENDING_PAYMENT
                || st == ExpertBookingStatus.PENDING_EXPERT) {
            booking.setStatus(ExpertBookingStatus.REJECTED);
            booking.setMeetingRoomUrl(null);
        }
        booking.setRejectReason(why);
        bookingRepository.save(booking);
        seedRefundSupportAskForBank(booking, null);

        log.warn(
                "ADMIN_REFUND_REQUEST: bookingId={} orderCode={} amount={} reason={} — ops hoàn trên PayOS rồi mark settled",
                bookingId,
                payment.getOrderCode(),
                payment.getAmount(),
                why);
        return mapToResponse(booking);
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
        ensureHourlyServiceExpiresAt(booking);
        booking.setStatus(ExpertBookingStatus.IN_PROGRESS);
    }

    /** Hourly: hạn dùng = paidAt (hoặc now) + HOURLY_VALIDITY_DAYS. */
    private void ensureHourlyServiceExpiresAt(ExpertBooking booking) {
        if (booking.getBookingType() != ExpertBookingType.HOURLY) {
            return;
        }
        if (booking.getServiceExpiresAt() != null) {
            return;
        }
        LocalDateTime base = booking.getPaidAt() != null ? booking.getPaidAt() : LocalDateTime.now();
        booking.setServiceExpiresAt(base.plusDays(ExpertBookingPolicy.HOURLY_VALIDITY_DAYS));
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
        seedRefundSupportAskForBank(saved, null);
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
            if (!leaveLike) {
                if (isHourlyServiceExpired(booking)) {
                    throw new IllegalArgumentException(
                            "Gói theo giờ đã hết hạn dùng (" + ExpertBookingPolicy.HOURLY_VALIDITY_DAYS
                                    + " ngày).");
                }
                if (!hasCallQuotaRemaining(booking)) {
                    throw new IllegalArgumentException(
                            "Đã hết phút call của gói. Book thêm gói theo giờ để tiếp tục.");
                }
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
        // Option A: chỉ chat sau Accept
        if (status != ExpertBookingStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    status == ExpertBookingStatus.AWAITING_EXPERT
                            ? "Expert chưa nhận đơn — chưa mở chat. Vui lòng đợi Accept."
                            : "Không thể gửi tin nhắn ở trạng thái hiện tại");
        }
        if (isHourlyServiceExpired(booking)) {
            throw new IllegalArgumentException(
                    "Gói theo giờ đã hết hạn dùng (" + ExpertBookingPolicy.HOURLY_VALIDITY_DAYS
                            + " ngày). Không thể gửi tin mới.");
        }
        boolean isClient = booking.getClient().getId().equals(sender.getId());
        if (!isClient) {
            return; // expert luôn gửi được khi IN_PROGRESS (và chưa hết hạn gói)
        }
        if (!clientCanSendMessage(booking)) {
            throw new IllegalArgumentException(
                    "Đã hết hạn Q&A hoặc hết 8 tin hỏi làm rõ. "
                            + "Book gói theo giờ nếu cần trao đổi thêm.");
        }
    }

    private boolean clientCanSendMessage(ExpertBooking booking) {
        if (booking.getStatus() != ExpertBookingStatus.IN_PROGRESS) {
            return false;
        }
        if (isHourlyServiceExpired(booking)) {
            return false;
        }
        // Trước khi có feedback chính: client vẫn nhắn được (làm rõ brief) — chỉ sau Accept
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
        if (isHourlyServiceExpired(booking)) {
            return false;
        }
        int limitMin = booking.getCallMinutesLimit() != null && booking.getCallMinutesLimit() > 0
                ? booking.getCallMinutesLimit()
                : ExpertBookingPolicy.callMinutesFor(booking.getBookingType(), booking.getHourlyHours());
        long used = computeCallSecondsUsed(booking);
        return used < limitMin * 60L;
    }

    private boolean isHourlyServiceExpired(ExpertBooking booking) {
        return booking.getBookingType() == ExpertBookingType.HOURLY
                && booking.getServiceExpiresAt() != null
                && LocalDateTime.now().isAfter(booking.getServiceExpiresAt());
    }

    private String buildQuotaHint(ExpertBooking booking) {
        if (booking.getStatus() == ExpertBookingStatus.AWAITING_EXPERT) {
            return "Expert cần nhận đơn trong 24h sau thanh toán; quá hạn sẽ hoàn tiền. Chat và call chỉ mở sau khi Accept.";
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
        String hint = "Call còn ~" + (remSec / 60) + " phút (gói " + limit + " phút).";
        if (booking.getServiceExpiresAt() != null) {
            hint += " Hạn dùng gói: " + booking.getServiceExpiresAt()
                    + " (" + ExpertBookingPolicy.HOURLY_VALIDITY_DAYS + " ngày từ thanh toán).";
        }
        return hint;
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
                .serviceExpiresAt(booking.getServiceExpiresAt())
                .callMinutesLimit(callLimitMin)
                .clientQaMessagesUsed(qaUsed)
                .clientQaMessageLimit(ExpertBookingPolicy.REVIEW_QA_CLIENT_MESSAGES)
                .clientCanSendMessage(clientCanSendMessage(booking))
                .callSecondsUsed(callUsed)
                .callSecondsRemaining(callRemaining)
                .canCall(canCall)
                .quotaHint(buildQuotaHint(booking))
                .expertHourlyRate(expert.getHourlyRate())
                .refundBankName(booking.getRefundBankName())
                .refundAccountNumber(booking.getRefundAccountNumber())
                .refundAccountHolder(booking.getRefundAccountHolder())
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

    private static final String REFUND_ASK_STK_TEMPLATE =
            "Xin chào, đơn của bạn đang được xử lý hoàn tiền.\n\n"
                    + "Vui lòng gửi thông tin nhận tiền:\n"
                    + "• Ngân hàng\n"
                    + "• Số tài khoản (STK)\n"
                    + "• Tên chủ tài khoản\n\n"
                    + "Bạn có thể điền form STK trên trang Đơn hàng Expert, hoặc trả lời tin này.\n"
                    + "— WillA Support";

    @Override
    @Transactional(readOnly = true)
    public List<ExpertRefundSupportMessageResponse> listRefundSupportMessages(
            String userEmail, Long bookingId, boolean asAdmin) {
        ExpertBooking booking = loadBookingForRefundSupport(userEmail, bookingId, asAdmin);
        assertRefundSupportReadable(booking);
        return refundSupportMessageRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()).stream()
                .map(this::mapRefundSupportMessage)
                .toList();
    }

    @Override
    public ExpertRefundSupportMessageResponse sendRefundSupportMessage(
            String userEmail, Long bookingId, String content, boolean asAdmin) {
        ExpertBooking booking = loadBookingForRefundSupport(userEmail, bookingId, asAdmin);
        User sender = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (asAdmin) {
            if (sender.getRole() != Role.ADMIN) {
                throw new IllegalArgumentException("Chỉ admin mới gửi được tin CS hoàn tiền");
            }
        } else if (!booking.getClient().getId().equals(sender.getId())) {
            throw new IllegalArgumentException("Chỉ khách của đơn mới chat được với CS hoàn tiền");
        }
        assertRefundSupportWritable(booking);

        String text = trimOrNull(content);
        if (text == null) {
            throw new IllegalArgumentException("Nội dung tin nhắn trống");
        }
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }

        ExpertRefundSupportMessage message = refundSupportMessageRepository.save(
                ExpertRefundSupportMessage.builder()
                        .booking(booking)
                        .sender(sender)
                        .content(text)
                        .build());
        return mapRefundSupportMessage(message);
    }

    @Override
    public ExpertBookingResponse saveRefundBankDetails(
            String clientEmail, Long bookingId, ExpertRefundBankDetailsRequest request) {
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));
        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!booking.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Không phải đơn của bạn");
        }
        assertRefundSupportWritable(booking);

        String bank = request != null ? trimOrNull(request.getBankName()) : null;
        String stk = request != null ? trimOrNull(request.getAccountNumber()) : null;
        String holder = request != null ? trimOrNull(request.getAccountHolder()) : null;
        if (bank == null || stk == null || holder == null) {
            throw new IllegalArgumentException("Cần đủ Ngân hàng, STK và tên chủ tài khoản");
        }

        booking.setRefundBankName(bank);
        booking.setRefundAccountNumber(stk);
        booking.setRefundAccountHolder(holder);
        bookingRepository.save(booking);

        String summary = "Đã gửi STK nhận hoàn:\n"
                + "• Ngân hàng: " + bank + "\n"
                + "• STK: " + stk + "\n"
                + "• Chủ TK: " + holder;
        refundSupportMessageRepository.save(ExpertRefundSupportMessage.builder()
                .booking(booking)
                .sender(client)
                .content(summary)
                .build());

        return mapToResponse(booking);
    }

    private void seedRefundSupportAskForBank(ExpertBooking booking, User adminSender) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        Payment payment = booking.getPayment();
        if (payment == null || payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            return;
        }
        if (refundSupportMessageRepository.countByBookingId(booking.getId()) > 0) {
            return;
        }
        User sender = adminSender;
        if (sender == null || sender.getRole() != Role.ADMIN) {
            sender = userRepository.findFirstByRoleOrderByIdAsc(Role.ADMIN).orElse(null);
        }
        if (sender == null) {
            log.warn("No ADMIN user to seed refund CS message for booking {}", booking.getId());
            return;
        }
        refundSupportMessageRepository.save(ExpertRefundSupportMessage.builder()
                .booking(booking)
                .sender(sender)
                .content(REFUND_ASK_STK_TEMPLATE)
                .build());
    }

    private ExpertBooking loadBookingForRefundSupport(String userEmail, Long bookingId, boolean asAdmin) {
        ExpertBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking #" + bookingId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (asAdmin) {
            if (user.getRole() != Role.ADMIN) {
                throw new IllegalArgumentException("Admin only");
            }
            return booking;
        }
        if (!booking.getClient().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Không phải đơn của bạn");
        }
        return booking;
    }

    private void assertRefundSupportReadable(ExpertBooking booking) {
        Payment payment = booking.getPayment();
        if (payment == null) {
            throw new IllegalArgumentException("Đơn chưa có payment");
        }
        PaymentStatus st = payment.getStatus();
        if (st != PaymentStatus.REFUND_PENDING && st != PaymentStatus.REFUNDED) {
            throw new IllegalArgumentException("Chat CS hoàn tiền chỉ mở khi đang/đã hoàn tiền");
        }
    }

    private void assertRefundSupportWritable(ExpertBooking booking) {
        Payment payment = booking.getPayment();
        if (payment == null || payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            throw new IllegalArgumentException("Chỉ chat / gửi STK khi payment đang REFUND_PENDING");
        }
    }

    private ExpertRefundSupportMessageResponse mapRefundSupportMessage(ExpertRefundSupportMessage message) {
        User sender = message.getSender();
        boolean fromAdmin = sender.getRole() == Role.ADMIN;
        return ExpertRefundSupportMessageResponse.builder()
                .id(message.getId())
                .senderId(sender.getId())
                .senderName(fromAdmin ? "WillA Support" : sender.getFullName())
                .senderEmail(sender.getEmail())
                .fromAdmin(fromAdmin)
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
