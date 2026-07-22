package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.ExpertSelfProfileRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.ExpertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/experts")
@RequiredArgsConstructor
@Tag(name = "Experts", description = "Expert hỗ trợ toàn app Willa")
@SecurityRequirement(name = "bearerAuth")
public class ExpertController {

    private final ExpertService expertService;

    @GetMapping
    @Operation(summary = "Danh sách expert đang hoạt động (toàn app)")
    public ResponseEntity<ApiResponse> listExperts() {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Experts retrieved")
                    .data(expertService.listActiveExperts())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/platform")
    @Operation(summary = "Alias — danh sách expert toàn app")
    public ResponseEntity<ApiResponse> listPlatformExperts() {
        return listExperts();
    }

    @GetMapping("/me")
    @Operation(summary = "Hồ sơ expert của tài khoản đang đăng nhập")
    public ResponseEntity<ApiResponse> getMyProfile(Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Expert profile retrieved")
                    .data(expertService.getMyExpertProfile(authentication.getName()))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @PutMapping("/me")
    @Operation(summary = "Expert tự cập nhật hồ sơ")
    public ResponseEntity<ApiResponse> updateMyProfile(
            Authentication authentication,
            @RequestBody ExpertSelfProfileRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Expert profile updated")
                    .data(expertService.updateMyExpertProfile(authentication.getName(), request))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }

    @GetMapping("/{expertId}")
    @Operation(summary = "Chi tiết expert đang hoạt động")
    public ResponseEntity<ApiResponse> getExpert(@PathVariable Long expertId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Expert retrieved")
                    .data(expertService.getActiveExpert(expertId))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
