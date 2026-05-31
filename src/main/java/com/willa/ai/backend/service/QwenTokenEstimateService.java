package com.willa.ai.backend.service;

import java.util.List;

import com.willa.ai.backend.dto.response.QwenTokenEstimateResponse;
import com.willa.ai.backend.entity.Wallet;

public interface QwenTokenEstimateService {

    record ImageEstimateInput(byte[] bytes, String filename) {}

    /** Text chat (không ảnh). */
    QwenTokenEstimateResponse estimateTextChat(String userMessage);

    /**
     * Feedback Design: mỗi ảnh (kể cả từng trang PDF) ≈ nhiều lần gọi Qwen trên AI server.
     */
    QwenTokenEstimateResponse estimateFeedbackDesign(
            List<ImageEstimateInput> images,
            String userMessage);

    void requireSufficientBalance(Wallet wallet, long requiredTokens);
}
