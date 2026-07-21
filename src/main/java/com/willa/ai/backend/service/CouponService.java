package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.CouponRequest;
import com.willa.ai.backend.dto.response.CouponResponse;
import com.willa.ai.backend.dto.response.CouponValidationResponse;
import com.willa.ai.backend.entity.Coupon;
import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.User;

import java.util.List;

public interface CouponService {

    List<CouponResponse> listAll();

    CouponResponse create(CouponRequest request);

    CouponResponse update(Long id, CouponRequest request);

    void delete(Long id);

    String generateUniqueCode();

    CouponValidationResponse validateForCheckout(
            String code, Long planId, String planType, long baseAmount, String userEmail);

    Coupon lockForPayment(String code, Long planId, String planType, long baseAmount, String userEmail);

    long applyDiscount(Coupon coupon, long baseAmount);

    void markRedeemed(Coupon coupon, Payment payment, User user);
}
