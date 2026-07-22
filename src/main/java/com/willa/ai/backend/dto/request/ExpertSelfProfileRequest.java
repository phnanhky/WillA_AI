package com.willa.ai.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Expert tự cập nhật hồ sơ (không đổi email / isActive). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertSelfProfileRequest {

    private String fullName;
    private String avatarUrl;
    private String expertise;
    private String bio;
    private String headline;
    private String portfolioUrl;
    private Long reviewPrice;
    private Long hourlyRate;
}
