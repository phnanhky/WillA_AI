package com.willa.ai.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_experts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceExpert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null = expert platform (hỗ trợ user không dùng workspace). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Lĩnh vực chuyên môn (vd: UI/UX, Branding). */
    @Column(length = 500)
    private String expertise;

    @Column(columnDefinition = "TEXT")
    private String bio;

    /** Dòng mô tả ngắn dưới tên (vd: Senior UI Designer). */
    @Column(length = 200)
    private String headline;

    /** Link portfolio / Behance / Instagram / website. */
    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Giá review ấn phẩm (VND). Null = chưa mở dịch vụ review. */
    @Column(name = "review_price")
    private Long reviewPrice;

    /** Giá trao đổi theo giờ (VND/giờ). Null = chưa mở dịch vụ theo giờ. */
    @Column(name = "hourly_rate")
    private Long hourlyRate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
