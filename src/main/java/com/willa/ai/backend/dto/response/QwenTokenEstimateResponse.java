package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kết quả ước lượng token Qwen3-VL trước khi gọi AI (chỉ dùng trong request, không lưu DB). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QwenTokenEstimateResponse {

    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private int imageCount;
}
