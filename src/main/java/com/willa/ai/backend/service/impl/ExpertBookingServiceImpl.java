package com.willa.ai.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingAttachmentRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
import com.willa.ai.backend.dto.response.ExpertBookingAttachmentResponse;
import com.willa.ai.backend.dto.response.ExpertBookingCheckoutResponse;
import com.willa.ai.backend.dto.response.ExpertBookingMessageResponse;
import com.willa.ai.backend.dto.response.ExpertBookingResponse;
import com.willa.ai.backend.entity.ExpertBooking;
import com.willa.ai.backend.entity.ExpertBookingAttachment;
import com.willa.ai.backend.entity.ExpertBookingMessage;
import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkspaceExpert;
import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import com.willa.ai.backend.entity.enums.ExpertBookingType;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.repository.ExpertBookingAttachmentRepository;
import com.willa.ai.backend.repository.ExpertBookingMessageRepository;
import com.willa.ai.backend.repository.ExpertBookingRepository;
import com.willa.ai.backend.repository.PaymentRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspaceExpertRepository;
import com.willa.ai.backend.service.ExpertBookingRealtimeService;
import com.willa.ai.backend.service.ExpertBookingService;
import com.willa.ai.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.type.CheckoutResponseData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final WorkspaceExpertRepository expertRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ExpertBookingRealtimeService expertBookingRealtimeService;
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
                || booking.getStatus() == ExpertBookingStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    booking.getStatus() == ExpertBookingStatus.COMPLETED
                            ? "Booking đã hoàn tất — không thể thêm tài liệu"
                            : "Booking đã hủy hoặc bị từ chối");
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
                .filter(b -> b.getStatus() != ExpertBookingStatus.CANCELLED
                        && b.getStatus() != ExpertBookingStatus.REJECTED)
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ExpertBookingResponse updateByExpert(
            String expertUserEmail, Long bookingId, ExpertBookingFeedbackRequest request) {
        ExpertBooking booking = loadBookingForExpert(expertUserEmail, bookingId);
        if (booking.getStatus() == ExpertBookingStatus.PENDING_PAYMENT
                || booking.getStatus() == ExpertBookingStatus.CANCELLED
                || booking.getStatus() == ExpertBookingStatus.REJECTED) {
            throw new IllegalArgumentException("Booking chưa sẵn sàng để cập nhật");
        }

        if (request.getStatus() != null) {
            ExpertBookingStatus next = request.getStatus();
            if (next != ExpertBookingStatus.IN_PROGRESS && next != ExpertBookingStatus.COMPLETED) {
                throw new IllegalArgumentException("Trạng thái không hợp lệ");
            }
            booking.setStatus(next);
            if (next == ExpertBookingStatus.COMPLETED) {
                if (trimOrNull(request.getFeedback()) == null
                        && trimOrNull(booking.getExpertFeedback()) == null) {
                    throw new IllegalArgumentException(
                            "Cần nhập phản hồi trước khi hoàn tất hỗ trợ");
                }
                booking.setCompletedAt(LocalDateTime.now());
                // Khóa phòng họp sau khi hoàn tất
                booking.setMeetingRoomUrl(null);
            }
        }

        String feedback = trimOrNull(request.getFeedback());
        if (feedback != null) {
            booking.setExpertFeedback(feedback);
        }

        if (booking.getStatus() == ExpertBookingStatus.AWAITING_EXPERT && feedback != null) {
            booking.setStatus(ExpertBookingStatus.IN_PROGRESS);
        }

        ensureMeetingRoomUrlPersisted(booking);
        ExpertBooking saved = bookingRepository.save(booking);
        ExpertBookingResponse response = mapToResponse(saved);
        expertBookingRealtimeService.publishBookingUpdated(saved, response);
        return response;
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
        assertChatWritable(booking);

        String content = trimOrNull(request.getContent());
        if (content == null) {
            throw new IllegalArgumentException("Nội dung tin nhắn trống");
        }

        User sender = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

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
                || status == ExpertBookingStatus.REJECTED) {
            throw new IllegalArgumentException("Chat chưa khả dụng cho booking này");
        }
    }

    private void assertChatWritable(ExpertBooking booking) {
        ExpertBookingStatus status = booking.getStatus();
        // Chỉ chat khi đang hỗ trợ — sau COMPLETED khóa gửi tin
        if (status != ExpertBookingStatus.AWAITING_EXPERT
                && status != ExpertBookingStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Không thể gửi tin nhắn ở trạng thái hiện tại");
        }
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
                .meetingRoomUrl(resolveMeetingRoomUrl(booking))
                .rejectReason(booking.getRejectReason())
                .createdAt(booking.getCreatedAt())
                .completedAt(booking.getCompletedAt())
                .build();
    }

    /** Link Jitsi chỉ khi đang hỗ trợ — sau COMPLETED không trả room (khóa video). */
    private String resolveMeetingRoomUrl(ExpertBooking booking) {
        String url = trimOrNull(booking.getMeetingRoomUrl());
        ExpertBookingStatus status = booking.getStatus();
        if (status != ExpertBookingStatus.AWAITING_EXPERT
                && status != ExpertBookingStatus.IN_PROGRESS) {
            return null;
        }
        if (url != null) {
            return url;
        }
        return buildMeetingRoomUrl(booking.getId());
    }

    private void ensureMeetingRoomUrlPersisted(ExpertBooking booking) {
        if (trimOrNull(booking.getMeetingRoomUrl()) != null) {
            return;
        }
        ExpertBookingStatus status = booking.getStatus();
        if (status == ExpertBookingStatus.AWAITING_EXPERT
                || status == ExpertBookingStatus.IN_PROGRESS) {
            booking.setMeetingRoomUrl(buildMeetingRoomUrl(booking.getId()));
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

    public static String buildMeetingRoomUrl(Long bookingId) {
        return "https://meet.jit.si/WillaBooking" + bookingId;
    }
}
