package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.AddUserLibraryImageRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.UserLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/library-images")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User library", description = "Thư viện ảnh cá nhân (dùng chung mọi workspace)")
@SecurityRequirement(name = "bearerAuth")
public class UserLibraryController {
    private final UserLibraryService userLibraryService;

    @GetMapping
    @Operation(summary = "Danh sách ảnh đã upload của user hiện tại")
    public ResponseEntity<ApiResponse> getMyLibraryImages(Authentication auth) {
        try {
            if (auth == null || auth.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.builder()
                        .status(false)
                        .message("Unauthorized")
                        .build());
            }
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Library images retrieved")
                    .data(userLibraryService.getUserLibraryImages(auth.getName()))
                    .build());
        } catch (RuntimeException e) {
            log.error("getMyLibraryImages failed for {}", auth != null ? auth.getName() : "?", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("getMyLibraryImages unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Failed to load library images")
                    .build());
        }
    }

    @PostMapping
    @Operation(summary = "Thêm ảnh vào thư viện cá nhân (sau khi upload file)")
    public ResponseEntity<ApiResponse> addMyLibraryImage(
            Authentication auth,
            @Valid @RequestBody AddUserLibraryImageRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Library image added")
                    .data(userLibraryService.addUserLibraryImage(auth.getName(), request))
                    .build());
        } catch (RuntimeException e) {
            log.error("addMyLibraryImage failed for {}", auth.getName(), e);
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("addMyLibraryImage unexpected error for {}", auth.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Failed to save library image")
                    .build());
        }
    }
}
