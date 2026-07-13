package com.willa.ai.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "expert_booking_call_events", indexes = {
        @Index(name = "idx_expert_call_evt_booking", columnList = "booking_id"),
        @Index(name = "idx_expert_call_evt_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertBookingCallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private ExpertBooking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "room_name", length = 200)
    private String roomName;

    @Column(name = "client_session_id", length = 100)
    private String clientSessionId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
