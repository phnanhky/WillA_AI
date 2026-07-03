package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.ValidateCouponRequest;
import com.willa.ai.backend.dto.ApiResponse;
import com.willa.ai.backend.dto.response.CouponValidationResponse;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.WorkspacePlan;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.WorkspacePlanRepository;
import com.willa.ai.backend.service.CouponService;
import com.willa.ai.backend.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.Webhook;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin("*")
@Tag(name = "Payment Management", description = "APIs for Payment Management")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private WorkspacePlanRepository workspacePlanRepository;

    @PostMapping("/validate-coupon")
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validateCoupon(
            @RequestBody ValidateCouponRequest request) {
        try {
            if (request.getCode() == null || request.getCode().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.<CouponValidationResponse>builder()
                        .success(false)
                        .message("Thiếu mã giảm giá")
                        .build());
            }
            if (request.getPlanId() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.<CouponValidationResponse>builder()
                        .success(false)
                        .message("Thiếu planId")
                        .build());
            }
            String planType = request.getPlanType() != null ? request.getPlanType() : "FEEDBACK";
            long listPrice = resolveListPrice(request.getPlanId(), planType);
            long adminPrice = resolveAdminPrice(request.getPlanId(), planType);
            CouponValidationResponse result = couponService.validateForCheckout(
                    request.getCode(), request.getPlanId(), planType, listPrice);
            result.setAdminDiscountPrice(adminPrice);
            if (result.isValid() && adminPrice < listPrice && adminPrice < result.getFinalAmount()) {
                result.setMessage(
                        "Mã hợp lệ. Giảm giá admin đang tốt hơn — xóa mã để thanh toán "
                                + adminPrice + " VND.");
            }
            return ResponseEntity.ok(ApiResponse.<CouponValidationResponse>builder()
                    .success(result.isValid())
                    .data(result)
                    .message(result.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<CouponValidationResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/create-link")
    public ResponseEntity<ApiResponse<CheckoutResponseData>> createPaymentLink(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            if (!request.containsKey("planId") || request.get("planId") == null) {
                return ResponseEntity.badRequest().body(ApiResponse.<CheckoutResponseData>builder()
                        .success(false)
                        .message("Missing 'planId' in request body")
                        .build());
            }
            
            Long planId = Long.parseLong(request.get("planId").toString());
            String planType = request.containsKey("planType") && request.get("planType") != null
                    ? request.get("planType").toString()
                    : "FEEDBACK";
            String couponCode = request.containsKey("couponCode") && request.get("couponCode") != null
                    ? request.get("couponCode").toString()
                    : null;
            String email = authentication.getName();

            CheckoutResponseData result = paymentService.createPaymentLink(email, planId, planType, couponCode);

            return ResponseEntity.ok(ApiResponse.<CheckoutResponseData>builder()
                    .success(true)
                    .data(result)
                    .message("Payment link generated successfully")
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(ApiResponse.<CheckoutResponseData>builder()
                    .success(false)
                    .message("Failed to generate payment link: " + e.getMessage())
                    .build());
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> webhookHandler(@RequestBody Webhook webhookData) {
        try {
            paymentService.handleWebhook(webhookData);

            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .success(true)
                    .data("OK")
                    .message("Webhook processed")
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .success(false)
                    .message("Webhook error: " + e.getMessage())
                    .build());
        }
    }

    private long resolveListPrice(Long planId, String planType) {
        boolean isWorkspace = planType != null && planType.equalsIgnoreCase("WORKSPACE");
        if (isWorkspace) {
            WorkspacePlan plan = workspacePlanRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace plan not found"));
            return plan.getPrice() != null ? plan.getPrice().longValue() : 0L;
        }
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        return plan.getPrice() != null ? plan.getPrice().longValue() : 0L;
    }

    private long resolveAdminPrice(Long planId, String planType) {
        boolean isWorkspace = planType != null && planType.equalsIgnoreCase("WORKSPACE");
        if (isWorkspace) {
            WorkspacePlan plan = workspacePlanRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace plan not found"));
            return resolvePaymentAmount(plan.getPrice(), plan.getPromotionalPrice());
        }
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        return resolvePaymentAmount(plan.getPrice(), plan.getPromotionalPrice());
    }

    private long resolvePaymentAmount(BigDecimal price, BigDecimal promotionalPrice) {
        if (promotionalPrice != null && promotionalPrice.compareTo(BigDecimal.ZERO) >= 0) {
            return promotionalPrice.longValue();
        }
        return price != null ? price.longValue() : 0L;
    }
}
