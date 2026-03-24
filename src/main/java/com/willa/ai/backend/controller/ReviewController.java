package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.ReviewRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.ReviewResponse;
import com.willa.ai.backend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Review", description = "Review management APIs")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse> createReview(
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        ReviewResponse createdReview = reviewService.createReview(email, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Review created successfully")
                .data(createdReview)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getReviewById(@PathVariable Long id) {
        ReviewResponse review = reviewService.getReviewById(id);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Review retrieved successfully")
                .data(review)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReviewResponse> reviews = reviewService.getAllReviews(page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Reviews retrieved successfully")
                .data(reviews)
                .build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getReviewsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReviewResponse> reviews = reviewService.getReviewsByUser(userId, page, size);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("User reviews retrieved successfully")
                .data(reviews)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        ReviewResponse updatedReview = reviewService.updateReview(id, email, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Review updated successfully")
                .data(updatedReview)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteReview(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        reviewService.deleteReview(id, email);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Review deleted successfully")
                .build());
    }
}
