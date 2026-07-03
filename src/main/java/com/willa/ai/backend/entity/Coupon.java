package com.willa.ai.backend.entity;

import com.willa.ai.backend.entity.enums.CouponDiscountType;
import com.willa.ai.backend.entity.enums.CouponPlanScope;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons", indexes = {
        @Index(name = "idx_coupons_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponDiscountType discountType;

    /** PERCENT: 0–100; FIXED_AMOUNT: VND. */
    @Column(name = "discount_value", nullable = false)
    private Long discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_scope", nullable = false, length = 20)
    @Builder.Default
    private CouponPlanScope planScope = CouponPlanScope.ALL;

    /** Gói cụ thể (feedback plan id hoặc workspace plan id) — null = mọi gói trong scope. */
    @Column(name = "plan_id")
    private Long planId;

    @Column(length = 500)
    private String note;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_by_user_id")
    private User redeemedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_payment_id")
    private Payment redeemedPayment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isRedeemed() {
        return redeemedAt != null;
    }
}
