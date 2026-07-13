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

    /** Tên hiển thị — bắt buộc khi tạo tài khoản mới. */
    private String fullName;

    /** Avatar URL (tùy chọn). */
    private String avatarUrl;

    /** Bỏ trống = expert platform (hỗ trợ user không dùng workspace). */
    private Long workspaceId;

    private String expertise;

    private String bio;

    private Boolean isActive;

    /** Giá review ấn phẩm (VND). */
    private Long reviewPrice;

    /** Giá theo giờ (VND/giờ). */
    private Long hourlyRate;

    /**
     * true (mặc định): nếu email chưa có User thì tạo tài khoản.
     * false: chỉ promote User đã tồn tại (hành vi cũ).
     */
    private Boolean createAccountIfMissing;

    /**
     * true (mặc định): gửi email mời / thông tin đăng nhập.
     */
    private Boolean sendInviteEmail;
}
