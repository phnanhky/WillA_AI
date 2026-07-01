package com.willa.ai.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceExpertRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    private String email;

    /** Bỏ trống = expert platform (hỗ trợ user không dùng workspace). */
    private Long workspaceId;

    private String expertise;

    private String bio;

    private Boolean isActive;

    /** Giá review ấn phẩm (VND). */
    private Long reviewPrice;

    /** Giá theo giờ (VND/giờ). */
    private Long hourlyRate;
}
