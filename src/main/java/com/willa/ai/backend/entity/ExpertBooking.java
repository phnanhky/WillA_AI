package com.willa.ai.backend.entity;

import com.willa.ai.backend.entity.enums.ExpertBookingStatus;
import com.willa.ai.backend.entity.enums.ExpertBookingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expert_bookings", indexes = {
        @Index(name = "idx_expert_bookings_client", columnList = "client_user_id"),
        @Index(name = "idx_expert_bookings_expert", columnList = "expert_id"),
        @Index(name = "idx_expert_bookings_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_user_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expert_id", nullable = false)
    private WorkspaceExpert expert;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 20)
    private ExpertBookingType bookingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ExpertBookingStatus status = ExpertBookingStatus.PENDING_EXPERT;

    /** Mô tả yêu cầu / câu hỏi của người dùng. */
    @Column(columnDefinition = "TEXT")
    private String brief;

    /** Ấn phẩm cần review (link, mô tả — có thể nhiều). */
    @Column(columnDefinition = "TEXT")
    private String publications;

    /** JSON array link Google Drive / Docs. */
    @Column(name = "drive_links", columnDefinition = "TEXT")
    private String driveLinks;

    /** Phản hồi từ expert sau khi xem xét. */
    @Column(name = "expert_feedback", columnDefinition = "TEXT")
    private String expertFeedback;

    /** Số giờ trao đổi (chỉ HOURLY). */
    @Column(name = "hourly_hours")
    private Integer hourlyHours;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_booking_id")
    private ExpertBooking parentBooking;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Phòng video call (Jitsi Meet) — tạo sau khi thanh toán. */
    @Column(name = "meeting_room_url")
    private String meetingRoomUrl;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExpertBookingAttachment> attachments = new ArrayList<>();
}
