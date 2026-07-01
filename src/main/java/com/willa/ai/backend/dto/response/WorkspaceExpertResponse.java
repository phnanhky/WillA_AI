package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceExpertResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String userAvatarUrl;
    /** Null nếu expert platform (không gắn workspace). */
    private Long workspaceId;
    private String workspaceTitle;
    /** true = hỗ trợ user không dùng workspace */
    private Boolean platformExpert;
    private String expertise;
    private String bio;
    private Boolean isActive;
    private LocalDateTime createdAt;
    /** Giá review ấn phẩm (VND). Mặc định 49.000. */
    private Long reviewPrice;
    /** Giá trao đổi theo giờ (VND/giờ). */
    private Long hourlyRate;
}
