package com.willa.ai.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "expert_booking_attachments", indexes = {
        @Index(name = "idx_expert_booking_att_booking", columnList = "booking_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertBookingAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private ExpertBooking booking;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
