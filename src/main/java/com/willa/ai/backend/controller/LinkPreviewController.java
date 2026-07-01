package com.willa.ai.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.LinkPreviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/link-preview")
@RequiredArgsConstructor
@Tag(name = "Link preview", description = "Lấy tiêu đề từ URL (Google Docs, Drive, …)")
@SecurityRequirement(name = "bearerAuth")
public class LinkPreviewController {

    private final LinkPreviewService linkPreviewService;

    @GetMapping
    @Operation(summary = "Lấy tiêu đề trang từ URL")
    public ResponseEntity<ApiResponse> resolveTitle(
            Authentication auth,
            @RequestParam String url) {
        try {
            if (auth == null || auth.getName() == null) {
                return ResponseEntity.status(401).body(ApiResponse.builder()
                        .status(false)
                        .message("Unauthorized")
                        .build());
            }
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Link preview resolved")
                    .data(linkPreviewService.resolveTitle(url))
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(false)
                    .message(e.getMessage())
                    .build());
        }
    }
}
