package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.service.ExpertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/experts")
@RequiredArgsConstructor
@Tag(name = "Experts", description = "Expert hướng dẫn (platform + workspace)")
@SecurityRequirement(name = "bearerAuth")
public class ExpertController {

    private final ExpertService expertService;

    @GetMapping("/platform")
    @Operation(summary = "Expert platform — hỗ trợ user không dùng workspace")
    public ResponseEntity<ApiResponse> listPlatformExperts() {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Platform experts retrieved")
                    .data(expertService.listPlatformExperts())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().status(false).message(e.getMessage()).build());
        }
    }
}
