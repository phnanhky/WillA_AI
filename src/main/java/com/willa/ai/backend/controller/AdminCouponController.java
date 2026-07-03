package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.CouponRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "Admin Coupons", description = "Quản lý mã giảm giá gói")
@SecurityRequirement(name = "bearerAuth")
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping
    @Operation(summary = "Danh sách mã giảm giá")
    public ResponseEntity<ApiResponse> list() {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Coupons fetched")
                    .data(couponService.listAll())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/generate-code")
    @Operation(summary = "Sinh mã coupon ngẫu nhiên")
    public ResponseEntity<ApiResponse> generateCode() {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Code generated")
                    .data(couponService.generateUniqueCode())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping
    @Operation(summary = "Tạo mã giảm giá")
    public ResponseEntity<ApiResponse> create(@RequestBody CouponRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Coupon created")
                    .data(couponService.create(request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật mã giảm giá")
    public ResponseEntity<ApiResponse> update(@PathVariable Long id, @RequestBody CouponRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Coupon updated")
                    .data(couponService.update(id, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa mã giảm giá")
    public ResponseEntity<ApiResponse> delete(@PathVariable Long id) {
        try {
            couponService.delete(id);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Coupon deleted")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
