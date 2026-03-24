package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.ReviewRequest;
import com.willa.ai.backend.dto.response.ReviewResponse;
import org.springframework.data.domain.Page;

public interface ReviewService {
    ReviewResponse createReview(String email, ReviewRequest request);
    ReviewResponse getReviewById(Long id);
    Page<ReviewResponse> getAllReviews(int page, int size);
    Page<ReviewResponse> getReviewsByUser(Long userId, int page, int size);
    ReviewResponse updateReview(Long id, String email, ReviewRequest request);
    void deleteReview(Long id, String email);
}
