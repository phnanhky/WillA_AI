package com.willa.ai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Snapshot persona đã sanitize — một bản ghi / user.
 * Không lưu email, phone, URL ảnh, hay nội dung chat thô.
 */
@Entity
@Table(name = "user_personas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPersona {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** JSON đã lọc — chỉ field whitelist, dùng inject AI. */
    @Column(name = "ai_context_json", columnDefinition = "TEXT")
    private String aiContextJson;

    /** Tóm tắt an toàn hiển thị UI (không PII). */
    @Column(name = "display_summary", length = 500)
    private String displaySummary;

    @Column(name = "analysis_count_used")
    @Builder.Default
    private Integer analysisCountUsed = 0;

    @Column(name = "signal_version")
    @Builder.Default
    private Integer signalVersion = 1;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_refreshed_at")
    private LocalDateTime lastRefreshedAt;

    @PrePersist
    @PreUpdate
    void touchTimestamps() {
        updatedAt = LocalDateTime.now();
        if (lastRefreshedAt == null) {
            lastRefreshedAt = updatedAt;
        }
    }
}
