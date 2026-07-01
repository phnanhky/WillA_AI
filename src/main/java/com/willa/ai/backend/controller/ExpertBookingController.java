package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
import com.willa.ai.backend.dto.request.ExpertBookingRejectRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.ExpertBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expert-bookings")
@RequiredArgsConstructor
@Tag(name = "Expert Bookings", description = "Đặt lịch review ấn phẩm / trao đổi theo giờ với expert")
@SecurityRequirement(name = "bearerAuth")
public class ExpertBookingController {

    private final ExpertBookingService expertBookingService;

    @PostMapping
    @Operation(summary = "Tạo yêu cầu booking — expert xem xét trước khi thanh toán")
    public ResponseEntity<ApiResponse> createBooking(
            @RequestBody CreateExpertBookingRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Booking created")
                    .data(expertBookingService.createBooking(authentication.getName(), request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{bookingId}/checkout")
    @Operation(summary = "Lấy link thanh toán PayOS (sau khi expert chấp nhận)")
    public ResponseEntity<ApiResponse> getCheckout(
            @PathVariable Long bookingId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Checkout retrieved")
                    .data(expertBookingService.getCheckoutForClient(authentication.getName(), bookingId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{bookingId}/accept")
    @Operation(summary = "Expert chấp nhận yêu cầu booking")
    public ResponseEntity<ApiResponse> acceptByExpert(
            @PathVariable Long bookingId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Booking accepted")
                    .data(expertBookingService.acceptByExpert(authentication.getName(), bookingId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{bookingId}/reject")
    @Operation(summary = "Expert từ chối yêu cầu booking")
    public ResponseEntity<ApiResponse> rejectByExpert(
            @PathVariable Long bookingId,
            @RequestBody(required = false) ExpertBookingRejectRequest request,
            Authentication authentication) {
        try {
            String reason = request != null ? request.getReason() : null;
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Booking rejected")
                    .data(expertBookingService.rejectByExpert(authentication.getName(), bookingId, reason))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/mine")
    @Operation(summary = "Booking của tôi (người dùng)")
    public ResponseEntity<ApiResponse> listMine(Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Bookings retrieved")
                    .data(expertBookingService.listMyBookings(authentication.getName()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/assigned")
    @Operation(summary = "Booking được gán cho expert hiện tại")
    public ResponseEntity<ApiResponse> listAssigned(Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Assigned bookings retrieved")
                    .data(expertBookingService.listAssignedBookings(authentication.getName()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PatchMapping("/{bookingId}/expert")
    @Operation(summary = "Expert cập nhật trạng thái / phản hồi")
    public ResponseEntity<ApiResponse> updateByExpert(
            @PathVariable Long bookingId,
            @RequestBody ExpertBookingFeedbackRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Booking updated")
                    .data(expertBookingService.updateByExpert(authentication.getName(), bookingId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{bookingId}/materials")
    @Operation(summary = "Khách gửi thêm file / link Drive")
    public ResponseEntity<ApiResponse> addMaterials(
            @PathVariable Long bookingId,
            @RequestBody AddExpertBookingMaterialsRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Materials added")
                    .data(expertBookingService.addMaterials(authentication.getName(), bookingId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{bookingId}/messages")
    @Operation(summary = "Tin nhắn chat trong booking")
    public ResponseEntity<ApiResponse> listMessages(
            @PathVariable Long bookingId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Messages retrieved")
                    .data(expertBookingService.listMessages(authentication.getName(), bookingId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PostMapping("/{bookingId}/messages")
    @Operation(summary = "Gửi tin nhắn trong booking")
    public ResponseEntity<ApiResponse> sendMessage(
            @PathVariable Long bookingId,
            @RequestBody ExpertBookingMessageRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Message sent")
                    .data(expertBookingService.sendMessage(authentication.getName(), bookingId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
