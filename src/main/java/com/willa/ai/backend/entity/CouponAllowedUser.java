package com.willa.ai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coupon_allowed_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_allowed_user", columnNames = {"coupon_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponAllowedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
