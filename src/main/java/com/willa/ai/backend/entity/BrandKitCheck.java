package com.willa.ai.backend.entity;

import com.willa.ai.backend.entity.enums.BrandKitCheckStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "brand_kit_checks", indexes = {
        @Index(name = "idx_brand_kit_checks_user", columnList = "user_id"),
        @Index(name = "idx_brand_kit_checks_profile", columnList = "profile_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandKitCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private BrandKitProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BrandKitCheckStatus status = BrandKitCheckStatus.COMPLETED;

    @Column(name = "avg_brand_score", precision = 5, scale = 1)
    private BigDecimal avgBrandScore;

    @Column(name = "total_assets", nullable = false)
    @Builder.Default
    private Integer totalAssets = 0;

    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
