package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.CreateBrandKitProfileRequest;
import com.willa.ai.backend.dto.request.UpdateBrandKitProfileRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.BrandKitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/brand-kit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Brand Kit", description = "Mini Brand Kit — lưu Visual DNA và lịch sử kiểm tra đồng bộ thương hiệu")
@SecurityRequirement(name = "bearerAuth")
public class BrandKitController {

    private final BrandKitService brandKitService;

    @GetMapping("/profiles")
    @Operation(summary = "Danh sách brand kit profile của user")
    public ResponseEntity<ApiResponse> listProfiles(Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand kit profiles retrieved")
                    .data(brandKitService.listProfiles(auth.getName()))
                    .build());
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/profiles/{profileId}")
    @Operation(summary = "Chi tiết brand kit profile")
    public ResponseEntity<ApiResponse> getProfile(Authentication auth, @PathVariable Long profileId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand kit profile retrieved")
                    .data(brandKitService.getProfile(auth.getName(), profileId))
                    .build());
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/profiles")
    @Operation(summary = "Tạo brand kit profile mới")
    public ResponseEntity<ApiResponse> createProfile(
            Authentication auth,
            @Valid @RequestBody CreateBrandKitProfileRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand kit profile created")
                    .data(brandKitService.createProfile(auth.getName(), request))
                    .build());
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @PatchMapping("/profiles/{profileId}")
    @Operation(summary = "Đổi tên brand kit profile")
    public ResponseEntity<ApiResponse> updateProfile(
            Authentication auth,
            @PathVariable Long profileId,
            @Valid @RequestBody UpdateBrandKitProfileRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand kit profile updated")
                    .data(brandKitService.updateProfile(auth.getName(), profileId, request))
                    .build());
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @DeleteMapping("/profiles/{profileId}")
    @Operation(summary = "Xóa brand kit profile")
    public ResponseEntity<ApiResponse> deleteProfile(Authentication auth, @PathVariable Long profileId) {
        try {
            brandKitService.deleteProfile(auth.getName(), profileId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand kit profile deleted")
                    .build());
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @PostMapping(value = "/checks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Chạy kiểm tra đồng bộ thương hiệu (lưu report + ảnh)")
    public ResponseEntity<ApiResponse> runCheck(
            Authentication auth,
            @RequestParam("ref_images") List<MultipartFile> refImages,
            @RequestParam("check_images") List<MultipartFile> checkImages,
            @RequestParam(value = "profileId", required = false) Long profileId,
            @RequestParam(value = "profileTitle", required = false) String profileTitle) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand check completed")
                    .data(brandKitService.runCheck(
                            auth.getName(), profileId, profileTitle, refImages, checkImages))
                    .build());
        } catch (RuntimeException e) {
            log.error("Brand check failed for {}", auth.getName(), e);
            return badRequest(e);
        }
    }

    @GetMapping("/checks")
    @Operation(summary = "Lịch sử kiểm tra brand kit")
    public ResponseEntity<ApiResponse> listChecks(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand checks retrieved")
                    .data(brandKitService.listChecks(auth.getName(), page, size))
                    .build());
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/checks/{checkId}")
    @Operation(summary = "Xem lại report brand check")
    public ResponseEntity<ApiResponse> getCheck(Authentication auth, @PathVariable Long checkId) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Brand check retrieved")
                    .data(brandKitService.getCheck(auth.getName(), checkId))
                    .build());
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    private ResponseEntity<ApiResponse> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.builder()
                .status(false)
                .message(e.getMessage())
                .build());
    }
}
