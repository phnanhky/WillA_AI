package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.CouponRequest;
import com.willa.ai.backend.dto.response.CouponResponse;
import com.willa.ai.backend.dto.response.CouponValidationResponse;
import com.willa.ai.backend.entity.Coupon;
import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.CouponDiscountType;
import com.willa.ai.backend.entity.enums.CouponPlanScope;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.repository.CouponRepository;
import com.willa.ai.backend.repository.PaymentRepository;
import com.willa.ai.backend.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class CouponServiceImpl implements CouponService {

    private static final long MIN_PAYMENT_VND = 1_000L;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CouponRepository couponRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> listAll() {
        return couponRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapResponse)
                .toList();
    }

    @Override
    public CouponResponse create(CouponRequest request) {
        validateRequest(request, true);
        String code = resolveCode(request, true);
        Coupon coupon = Coupon.builder()
                .code(code)
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .planScope(request.getPlanScope() != null ? request.getPlanScope() : CouponPlanScope.ALL)
                .planId(request.getPlanId())
                .note(trim(request.getNote()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .expiresAt(request.getExpiresAt())
                .build();
        return mapResponse(couponRepository.save(coupon));
    }

    @Override
    public CouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá"));
        if (coupon.isRedeemed()) {
            throw new IllegalArgumentException("Mã đã sử dụng — không thể sửa");
        }
        validateRequest(request, false);
        if (request.getDiscountType() != null) {
            coupon.setDiscountType(request.getDiscountType());
        }
        if (request.getDiscountValue() != null) {
            coupon.setDiscountValue(request.getDiscountValue());
        }
        if (request.getPlanScope() != null) {
            coupon.setPlanScope(request.getPlanScope());
        }
        coupon.setPlanId(request.getPlanId());
        if (request.getNote() != null) {
            coupon.setNote(trim(request.getNote()));
        }
        if (request.getIsActive() != null) {
            coupon.setIsActive(request.getIsActive());
        }
        if (request.getExpiresAt() != null) {
            coupon.setExpiresAt(request.getExpiresAt());
        }
        return mapResponse(couponRepository.save(coupon));
    }

    @Override
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá"));
        if (coupon.isRedeemed()) {
            throw new IllegalArgumentException("Mã đã sử dụng — không thể xóa");
        }
        couponRepository.delete(coupon);
    }

    @Override
    public String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = "WILLA-" + randomSuffix(8);
            if (!couponRepository.existsByCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không tạo được mã duy nhất");
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validateForCheckout(
            String code, Long planId, String planType, long baseAmount) {
        try {
            Coupon coupon = loadActiveCoupon(code);
            assertPlanMatch(coupon, planId, planType);
            assertNotRedeemed(coupon);
            assertNoPendingPayment(coupon);
            long finalAmount = applyDiscount(coupon, baseAmount);
            if (finalAmount > 0 && finalAmount < MIN_PAYMENT_VND) {
                return invalid(code, "Số tiền sau giảm phải tối thiểu 1.000 VND");
            }
            long discountAmount = Math.max(0, baseAmount - finalAmount);
            return CouponValidationResponse.builder()
                    .valid(true)
                    .code(coupon.getCode())
                    .discountType(coupon.getDiscountType())
                    .discountValue(coupon.getDiscountValue())
                    .originalAmount(baseAmount)
                    .discountAmount(discountAmount)
                    .finalAmount(finalAmount)
                    .message("Mã hợp lệ — áp dụng trên giá gốc, không cộng với giảm giá admin")
                    .build();
        } catch (IllegalArgumentException e) {
            return invalid(normalizeCode(code), e.getMessage());
        }
    }

    @Override
    public Coupon lockForPayment(String code, Long planId, String planType, long baseAmount) {
        Coupon coupon = couponRepository.findByCodeForUpdate(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại"));
        assertActive(coupon);
        assertPlanMatch(coupon, planId, planType);
        assertNotRedeemed(coupon);
        assertNoPendingPayment(coupon);
        long finalAmount = applyDiscount(coupon, baseAmount);
        if (finalAmount > 0 && finalAmount < MIN_PAYMENT_VND) {
            throw new IllegalArgumentException("Số tiền sau giảm phải tối thiểu 1.000 VND");
        }
        return coupon;
    }

    @Override
    public long applyDiscount(Coupon coupon, long baseAmount) {
        if (baseAmount <= 0) {
            return 0;
        }
        long result;
        if (coupon.getDiscountType() == CouponDiscountType.PERCENT) {
            long pct = Math.min(100, Math.max(0, coupon.getDiscountValue()));
            result = baseAmount - (baseAmount * pct / 100);
        } else {
            result = baseAmount - coupon.getDiscountValue();
        }
        return Math.max(0, result);
    }

    @Override
    public void markRedeemed(Coupon coupon, Payment payment, User user) {
        if (coupon.isRedeemed()) {
            return;
        }
        coupon.setRedeemedAt(LocalDateTime.now());
        coupon.setRedeemedBy(user);
        coupon.setRedeemedPayment(payment);
        couponRepository.save(coupon);
    }

    private Coupon loadActiveCoupon(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại"));
        assertActive(coupon);
        return coupon;
    }

    private void assertActive(Coupon coupon) {
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            throw new IllegalArgumentException("Mã giảm giá đã bị vô hiệu hóa");
        }
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Mã giảm giá đã hết hạn");
        }
    }

    private void assertNotRedeemed(Coupon coupon) {
        if (coupon.isRedeemed()) {
            throw new IllegalArgumentException("Mã giảm giá đã được sử dụng");
        }
    }

    private void assertNoPendingPayment(Coupon coupon) {
        if (paymentRepository.existsByCoupon_IdAndStatus(coupon.getId(), PaymentStatus.PENDING)) {
            throw new IllegalArgumentException("Mã đang được giữ trong giao dịch chờ thanh toán");
        }
    }

    private void assertPlanMatch(Coupon coupon, Long planId, String planType) {
        boolean isWorkspace = planType != null && planType.equalsIgnoreCase("WORKSPACE");
        CouponPlanScope scope = coupon.getPlanScope();
        if (scope == CouponPlanScope.FEEDBACK && isWorkspace) {
            throw new IllegalArgumentException("Mã chỉ áp dụng cho gói Feedback");
        }
        if (scope == CouponPlanScope.WORKSPACE && !isWorkspace) {
            throw new IllegalArgumentException("Mã chỉ áp dụng cho gói Workspace");
        }
        if (coupon.getPlanId() != null && planId != null && !coupon.getPlanId().equals(planId)) {
            throw new IllegalArgumentException("Mã không áp dụng cho gói này");
        }
    }

    private void validateRequest(CouponRequest request, boolean creating) {
        if (request.getDiscountType() == null) {
            throw new IllegalArgumentException("Thiếu loại giảm giá");
        }
        if (request.getDiscountValue() == null || request.getDiscountValue() < 0) {
            throw new IllegalArgumentException("Giá trị giảm không hợp lệ");
        }
        if (request.getDiscountType() == CouponDiscountType.PERCENT && request.getDiscountValue() > 100) {
            throw new IllegalArgumentException("% giảm phải từ 0 đến 100");
        }
        if (creating && request.getDiscountType() == CouponDiscountType.FIXED_AMOUNT
                && request.getDiscountValue() == 0) {
            throw new IllegalArgumentException("Giảm cố định phải > 0");
        }
    }

    private String resolveCode(CouponRequest request, boolean creating) {
        boolean auto = Boolean.TRUE.equals(request.getAutoGenerateCode())
                || (creating && trim(request.getCode()) == null);
        if (auto) {
            return generateUniqueCode();
        }
        String code = normalizeCode(request.getCode());
        if (code.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã coupon");
        }
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Mã đã tồn tại");
        }
        return code;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private String randomSuffix(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private CouponValidationResponse invalid(String code, String message) {
        return CouponValidationResponse.builder()
                .valid(false)
                .code(code)
                .message(message)
                .build();
    }

    private CouponResponse mapResponse(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .planScope(coupon.getPlanScope())
                .planId(coupon.getPlanId())
                .note(coupon.getNote())
                .isActive(coupon.getIsActive())
                .expiresAt(coupon.getExpiresAt())
                .redeemed(coupon.isRedeemed())
                .redeemedAt(coupon.getRedeemedAt())
                .redeemedByEmail(coupon.getRedeemedBy() != null ? coupon.getRedeemedBy().getEmail() : null)
                .redeemedPaymentId(coupon.getRedeemedPayment() != null ? coupon.getRedeemedPayment().getId() : null)
                .createdAt(coupon.getCreatedAt())
                .build();
    }
}
