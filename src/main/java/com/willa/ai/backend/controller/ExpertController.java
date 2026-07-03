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
}
