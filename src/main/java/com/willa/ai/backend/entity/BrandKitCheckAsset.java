package com.willa.ai.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "brand_kit_check_assets", indexes = {
        @Index(name = "idx_brand_kit_check_assets_check", columnList = "check_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandKitCheckAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    private BrandKitCheck check;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "brand_score", precision = 5, scale = 1)
    private BigDecimal brandScore;

    @Column(length = 20)
    private String severity;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
