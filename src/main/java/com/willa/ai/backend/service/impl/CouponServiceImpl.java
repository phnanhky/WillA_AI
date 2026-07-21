package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.CouponPlanTarget;
import com.willa.ai.backend.dto.request.CouponRequest;
import com.willa.ai.backend.dto.response.CouponResponse;
import com.willa.ai.backend.dto.response.CouponValidationResponse;
import com.willa.ai.backend.entity.Coupon;
import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.WorkspacePlan;
import com.willa.ai.backend.entity.enums.CouponDiscountType;
import com.willa.ai.backend.entity.enums.CouponPlanScope;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.repository.CouponRepository;
import com.willa.ai.backend.repository.PaymentRepository;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WorkspacePlanRepository;
import com.willa.ai.backend.service.CouponService;
import com.willa.ai.backend.util.CouponJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CouponServiceImpl implements CouponService {

    private static final long MIN_PAYMENT_VND = 1_000L;
    private static final String CODE_PREFIX = "WILLA_";
    private static final int DEFAULT_GENERATED_SUFFIX_LENGTH = 12;
    private static final int MAX_CODE_LENGTH = 64;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CouponRepository couponRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final WorkspacePlanRepository workspacePlanRepository;
    private final CouponJsonCodec couponJsonCodec;

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
                .bonusDays(normalizeBonusDays(request.getBonusDays()))
                .planScope(request.getPlanScope() != null ? request.getPlanScope() : CouponPlanScope.ALL)
                .planId(resolveLegacyPlanId(request))
                .eligiblePlans(couponJsonCodec.encodePlans(request.getEligiblePlans()))
                .allowedUserIds(couponJsonCodec.encodeUserIds(normalizeUserIds(request.getAllowedUserIds())))
                .note(trim(request.getNote()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .startsAt(normalizeStartsAt(request.getStartsAt()))
                .expiresAt(normalizeExpiresAt(request.getExpiresAt()))
                .maxRedemptions(Boolean.TRUE.equals(request.getClearMaxRedemptions())
                        ? null
                        : normalizeMaxRedemptions(
                                request.getMaxRedemptions() != null ? request.getMaxRedemptions() : 1))
                .redemptionCount(0)
                .build();
        return mapResponse(couponRepository.save(coupon));
    }

    @Override
    public CouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá"));
        boolean fullUpdate = request.getDiscountType() != null;
        if (fullUpdate) {
            if (coupon.isRedeemed()) {
                throw new IllegalArgumentException("Mã đã hết lượt dùng — không thể sửa");
            }
            validateRequest(request, false);
        }
        if (request.getDiscountType() != null) {
            coupon.setDiscountType(request.getDiscountType());
        }
        if (request.getDiscountValue() != null) {
            coupon.setDiscountValue(request.getDiscountValue());
        }
        if (request.getBonusDays() != null) {
            coupon.setBonusDays(normalizeBonusDays(request.getBonusDays()));
        } else if (fullUpdate) {
            coupon.setBonusDays(null);
        }
        if (request.getPlanScope() != null) {
            coupon.setPlanScope(request.getPlanScope());
        }
        if (request.getEligiblePlans() != null) {
            coupon.setEligiblePlans(couponJsonCodec.encodePlans(request.getEligiblePlans()));
            coupon.setPlanId(resolveLegacyPlanId(request));
        } else if (fullUpdate) {
            coupon.setEligiblePlans(null);
            coupon.setPlanId(request.getPlanId());
        }
        if (request.getAllowedUserIds() != null) {
            coupon.setAllowedUserIds(couponJsonCodec.encodeUserIds(normalizeUserIds(request.getAllowedUserIds())));
        } else if (fullUpdate) {
            coupon.setAllowedUserIds(null);
        }
        if (request.getNote() != null) {
            coupon.setNote(trim(request.getNote()));
        }
        if (request.getIsActive() != null) {
            coupon.setIsActive(request.getIsActive());
        }
        if (fullUpdate) {
            coupon.setStartsAt(normalizeStartsAt(request.getStartsAt()));
            coupon.setExpiresAt(normalizeExpiresAt(request.getExpiresAt()));
            if (Boolean.TRUE.equals(request.getClearMaxRedemptions())) {
                coupon.setMaxRedemptions(null);
            } else {
                Integer maxRedemptions = normalizeMaxRedemptions(request.getMaxRedemptions());
                int used = coupon.getRedemptionCount() != null ? coupon.getRedemptionCount() : 0;
                if (maxRedemptions != null && maxRedemptions < used) {
                    throw new IllegalArgumentException(
                            "Giới hạn lượt dùng phải >= số lượt đã dùng (" + used + ")");
                }
                coupon.setMaxRedemptions(maxRedemptions);
            }
        }
        return mapResponse(couponRepository.save(coupon));
    }

    @Override
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá"));
        if (coupon.hasAnyRedemption()) {
            throw new IllegalArgumentException("Mã đã được sử dụng — không thể xóa");
        }
        couponRepository.delete(coupon);
    }

    @Override
    public String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = CODE_PREFIX + randomSuffix(DEFAULT_GENERATED_SUFFIX_LENGTH);
            if (!couponRepository.existsByCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không tạo được mã duy nhất");
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validateForCheckout(
            String code, Long planId, String planType, long baseAmount, String userEmail) {
        try {
            Coupon coupon = loadActiveCoupon(code);
            User user = resolveUser(userEmail);
            assertValidPeriod(coupon);
            assertUserAllowed(coupon, user);
            assertPlanMatch(coupon, planId, planType);
            assertUsageAvailable(coupon, user);
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
                    .bonusDays(coupon.getBonusDays())
                    .message(buildValidMessage(coupon))
                    .build();
        } catch (IllegalArgumentException e) {
            return invalid(normalizeCode(code), e.getMessage());
        }
    }

    @Override
    public Coupon lockForPayment(String code, Long planId, String planType, long baseAmount, String userEmail) {
        Coupon coupon = couponRepository.findByCodeForUpdate(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        assertActive(coupon);
        assertValidPeriod(coupon);
        assertUserAllowed(coupon, user);
        assertPlanMatch(coupon, planId, planType);
        assertUsageAvailable(coupon, user);
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
        int count = coupon.getRedemptionCount() != null ? coupon.getRedemptionCount() : 0;
        coupon.setRedemptionCount(count + 1);
        if (coupon.getRedeemedAt() == null) {
            coupon.setRedeemedAt(LocalDateTime.now());
            coupon.setRedeemedBy(user);
            coupon.setRedeemedPayment(payment);
        }
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
    }

    private void assertValidPeriod(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new IllegalArgumentException("Mã chưa có hiệu lực");
        }
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Mã giảm giá đã hết hạn");
        }
    }

    private void assertUsageAvailable(Coupon coupon, User user) {
        if (coupon.isRedeemed()) {
            throw new IllegalArgumentException("Mã giảm giá đã hết lượt sử dụng");
        }
        if (user != null && paymentRepository.existsByCoupon_IdAndUser_IdAndStatus(
                coupon.getId(), user.getId(), PaymentStatus.PAID)) {
            throw new IllegalArgumentException("Bạn đã sử dụng mã này rồi");
        }
    }

    private void assertNoPendingPayment(Coupon coupon) {
        if (paymentRepository.existsByCoupon_IdAndStatus(coupon.getId(), PaymentStatus.PENDING)) {
            throw new IllegalArgumentException("Mã đang được giữ trong giao dịch chờ thanh toán");
        }
    }

    private void assertUserAllowed(Coupon coupon, User user) {
        List<Long> allowed = couponJsonCodec.decodeUserIds(coupon.getAllowedUserIds());
        if (allowed.isEmpty()) {
            return;
        }
        if (user == null) {
            throw new IllegalArgumentException("Mã này yêu cầu đăng nhập");
        }
        if (!allowed.contains(user.getId())) {
            throw new IllegalArgumentException("Mã không áp dụng cho tài khoản này");
        }
    }

    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
    }

    private void assertPlanMatch(Coupon coupon, Long planId, String planType) {
        boolean isWorkspace = planType != null && planType.equalsIgnoreCase("WORKSPACE");
        String normalizedType = isWorkspace ? "WORKSPACE" : "FEEDBACK";
        CouponPlanScope scope = coupon.getPlanScope();
        if (scope == CouponPlanScope.FEEDBACK && isWorkspace) {
            throw new IllegalArgumentException("Mã chỉ áp dụng cho gói Feedback");
        }
        if (scope == CouponPlanScope.WORKSPACE && !isWorkspace) {
            throw new IllegalArgumentException("Mã chỉ áp dụng cho gói Workspace");
        }

        List<CouponPlanTarget> targets = couponJsonCodec.decodePlans(coupon.getEligiblePlans());
        if (!targets.isEmpty()) {
            boolean match = targets.stream().anyMatch(t ->
                    Objects.equals(t.getPlanId(), planId)
                            && t.getPlanType() != null
                            && t.getPlanType().equalsIgnoreCase(normalizedType));
            if (!match) {
                throw new IllegalArgumentException("Mã không áp dụng cho gói này");
            }
            return;
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
        Integer bonusDays = normalizeBonusDays(request.getBonusDays());
        if (bonusDays != null && bonusDays > 3650) {
            throw new IllegalArgumentException("Số ngày cộng thêm tối đa 3650");
        }
        if (request.getDiscountType() == CouponDiscountType.PERCENT && request.getDiscountValue() > 100) {
            throw new IllegalArgumentException("% giảm phải từ 0 đến 100");
        }
        boolean hasDiscount = hasMonetaryDiscount(request.getDiscountType(), request.getDiscountValue());
        boolean hasBonusDays = bonusDays != null && bonusDays > 0;
        if (creating && !hasDiscount && !hasBonusDays) {
            throw new IllegalArgumentException("Cần ít nhất giảm giá hoặc số ngày cộng thêm");
        }
        if (creating && request.getDiscountType() == CouponDiscountType.FIXED_AMOUNT
                && request.getDiscountValue() == 0 && !hasBonusDays) {
            throw new IllegalArgumentException("Giảm cố định phải > 0 hoặc kèm ngày cộng thêm");
        }
        if (request.getStartsAt() != null && request.getExpiresAt() != null) {
            if (request.getExpiresAt().isBefore(request.getStartsAt())) {
                throw new IllegalArgumentException("Ngày kết thúc phải từ ngày bắt đầu trở đi");
            }
        }
        Integer maxRedemptions = normalizeMaxRedemptions(request.getMaxRedemptions());
        if (maxRedemptions != null && maxRedemptions < 1) {
            throw new IllegalArgumentException("Giới hạn lượt dùng phải >= 1");
        }
        validateEligiblePlans(request.getEligiblePlans());
        validateAllowedUsers(normalizeUserIds(request.getAllowedUserIds()));
    }

    private void validateEligiblePlans(List<CouponPlanTarget> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        for (CouponPlanTarget target : plans) {
            if (target.getPlanId() == null || target.getPlanType() == null) {
                throw new IllegalArgumentException("Gói áp dụng không hợp lệ");
            }
            boolean isWorkspace = "WORKSPACE".equalsIgnoreCase(target.getPlanType());
            if (isWorkspace) {
                WorkspacePlan plan = workspacePlanRepository.findById(target.getPlanId())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy gói workspace #" + target.getPlanId()));
                if (!Boolean.TRUE.equals(plan.getIsActive())) {
                    throw new IllegalArgumentException("Gói workspace không active: " + plan.getName());
                }
            } else {
                Plan plan = planRepository.findById(target.getPlanId())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy gói feedback #" + target.getPlanId()));
                if (!Boolean.TRUE.equals(plan.getIsActive())) {
                    throw new IllegalArgumentException("Gói feedback không active: " + plan.getName());
                }
            }
        }
    }

    private void validateAllowedUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            if (!seen.add(userId)) {
                continue;
            }
            if (!userRepository.existsById(userId)) {
                throw new IllegalArgumentException("Không tìm thấy user #" + userId);
            }
        }
    }

    private List<Long> normalizeUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private Long resolveLegacyPlanId(CouponRequest request) {
        List<CouponPlanTarget> plans = request.getEligiblePlans();
        if (plans == null || plans.isEmpty()) {
            return request.getPlanId();
        }
        return plans.size() == 1 ? plans.get(0).getPlanId() : null;
    }

    private Integer normalizeMaxRedemptions(Integer maxRedemptions) {
        if (maxRedemptions == null || maxRedemptions <= 0) {
            return null;
        }
        return maxRedemptions;
    }

    private boolean hasMonetaryDiscount(CouponDiscountType type, Long value) {
        if (value == null || value <= 0) {
            return false;
        }
        return type == CouponDiscountType.FIXED_AMOUNT || type == CouponDiscountType.PERCENT;
    }

    private Integer normalizeBonusDays(Integer bonusDays) {
        if (bonusDays == null || bonusDays <= 0) {
            return null;
        }
        return bonusDays;
    }

    /** Ngày bắt đầu → 00:00:00 (cả ngày đó mở). */
    private LocalDateTime normalizeStartsAt(java.time.LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay();
    }

    /** Ngày kết thúc → 23:59:59 (hết hiệu lực cuối ngày). */
    private LocalDateTime normalizeExpiresAt(java.time.LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atTime(23, 59, 59);
    }

    private String buildValidMessage(Coupon coupon) {
        String base = "Mã hợp lệ — áp dụng trên giá gói hiện tại (đã gồm giảm giá admin nếu có)";
        Integer bonusDays = coupon.getBonusDays();
        if (bonusDays != null && bonusDays > 0) {
            base += ". Cộng thêm " + bonusDays + " ngày sử dụng gói";
        }
        return base;
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
        assertValidCodeFormat(code);
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Mã đã tồn tại");
        }
        return code;
    }

    private void assertValidCodeFormat(String code) {
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Mã tối đa " + MAX_CODE_LENGTH + " ký tự");
        }
        if (!code.startsWith(CODE_PREFIX)) {
            throw new IllegalArgumentException("Mã phải bắt đầu bằng WILLA_");
        }
        if (code.length() <= CODE_PREFIX.length()) {
            throw new IllegalArgumentException("Mã phải có phần hậu tố sau WILLA_");
        }
        String suffix = code.substring(CODE_PREFIX.length());
        if (!suffix.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Phần sau WILLA_ chỉ gồm chữ in hoa, số và gạch dưới (A-Z, 0-9, _)");
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("WILLA-")) {
            normalized = CODE_PREFIX + normalized.substring(6);
        }
        return normalized;
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
        List<Long> allowedUserIds = couponJsonCodec.decodeUserIds(coupon.getAllowedUserIds());
        List<String> allowedUserEmails = new ArrayList<>();
        for (Long userId : allowedUserIds) {
            userRepository.findById(userId).ifPresent(u -> allowedUserEmails.add(u.getEmail()));
        }

        List<CouponPlanTarget> eligiblePlans = enrichPlanNames(
                couponJsonCodec.decodePlans(coupon.getEligiblePlans()));

        Integer redemptionCount = coupon.getRedemptionCount() != null ? coupon.getRedemptionCount() : 0;
        Integer remainingUses = null;
        if (coupon.getMaxRedemptions() != null) {
            remainingUses = Math.max(0, coupon.getMaxRedemptions() - redemptionCount);
        }

        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .bonusDays(coupon.getBonusDays())
                .planScope(coupon.getPlanScope())
                .planId(coupon.getPlanId())
                .eligiblePlans(eligiblePlans)
                .allowedUserIds(allowedUserIds.isEmpty() ? null : allowedUserIds)
                .allowedUserEmails(allowedUserEmails.isEmpty() ? null : allowedUserEmails)
                .note(coupon.getNote())
                .isActive(coupon.getIsActive())
                .startsAt(coupon.getStartsAt())
                .expiresAt(coupon.getExpiresAt())
                .maxRedemptions(coupon.getMaxRedemptions())
                .redemptionCount(redemptionCount)
                .remainingUses(remainingUses)
                .redeemed(coupon.isRedeemed())
                .redeemedAt(coupon.getRedeemedAt())
                .redeemedByEmail(coupon.getRedeemedBy() != null ? coupon.getRedeemedBy().getEmail() : null)
                .redeemedPaymentId(coupon.getRedeemedPayment() != null ? coupon.getRedeemedPayment().getId() : null)
                .createdAt(coupon.getCreatedAt())
                .build();
    }

    private List<CouponPlanTarget> enrichPlanNames(List<CouponPlanTarget> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        List<CouponPlanTarget> enriched = new ArrayList<>();
        for (CouponPlanTarget target : targets) {
            String planName = null;
            if (target.getPlanType() != null && "WORKSPACE".equalsIgnoreCase(target.getPlanType())) {
                planName = workspacePlanRepository.findById(target.getPlanId())
                        .map(WorkspacePlan::getName)
                        .orElse(null);
            } else if (target.getPlanId() != null) {
                planName = planRepository.findById(target.getPlanId())
                        .map(Plan::getName)
                        .orElse(null);
            }
            enriched.add(CouponPlanTarget.builder()
                    .planType(target.getPlanType())
                    .planId(target.getPlanId())
                    .planName(planName)
                    .build());
        }
        return enriched;
    }
}
