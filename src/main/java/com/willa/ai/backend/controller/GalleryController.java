package com.willa.ai.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.GalleryItemResponse;
import com.willa.ai.backend.service.GallerySearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/gallery")
@RequiredArgsConstructor
@Tag(name = "Gallery", description = "Gallery search powered by Elasticsearch")
@SecurityRequirement(name = "bearerAuth")
public class GalleryController {

    private final GallerySearchService gallerySearchService;

    @GetMapping
    @Operation(summary = "Search gallery items (session title, description)")
    public ResponseEntity<ApiResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Page<GalleryItemResponse> results = gallerySearchService.search(authentication.getName(), q, page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Gallery items retrieved successfully")
                .data(results)
                .build());
    }
}
