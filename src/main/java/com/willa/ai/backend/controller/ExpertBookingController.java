package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.AddExpertBookingMaterialsRequest;
import com.willa.ai.backend.dto.request.CreateExpertBookingRequest;
import com.willa.ai.backend.dto.request.ExpertBookingCallEventRequest;
import com.willa.ai.backend.dto.request.ExpertBookingFeedbackRequest;
import com.willa.ai.backend.dto.request.ExpertBookingMessageRequest;
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
    @Operation(summary = "Tạo booking và lấy link thanh toán PayOS")
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
    @Operation(summary = "Lấy lại link thanh toán PayOS")
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

    @PostMapping("/{bookingId}/call-events")
    @Operation(summary = "Ghi event Jitsi (join/leave/mute/…)")
    public ResponseEntity<ApiResponse> recordCallEvent(
            @PathVariable Long bookingId,
            @RequestBody ExpertBookingCallEventRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Call event recorded")
                    .data(expertBookingService.recordCallEvent(authentication.getName(), bookingId, request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{bookingId}/call-history")
    @Operation(summary = "Lịch sử phiên gọi + event Jitsi")
    public ResponseEntity<ApiResponse> callHistory(
            @PathVariable Long bookingId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Call history retrieved")
                    .data(expertBookingService.getCallHistory(authentication.getName(), bookingId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
