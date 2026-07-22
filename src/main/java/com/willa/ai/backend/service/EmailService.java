package com.willa.ai.backend.service;

public interface EmailService {

    void sendSimpleEmail(String to, String subject, String body);

    void sendHtmlEmail(String to, String subject, String htmlContent);

    void sendVerificationEmail(String to, String verificationLink);

    void sendPasswordResetEmail(String to, String resetLink);

    void sendWelcomeEmail(String to, String fullName);

    void sendWorkspaceInviteEmail(String to, String workspaceName, String inviterName, String inviteLink, String role);

    /** Mời expert mới — kèm mật khẩu tạm (chỉ khi vừa tạo account). */
    void sendExpertInviteEmail(String to, String fullName, String plainPassword, String loginUrl);

    /** Báo cho User đã có rằng đã được thêm làm Expert (không gửi mật khẩu). */
    void sendExpertAssignedEmail(String to, String fullName, String loginUrl);

    /** Báo expert có đơn mới cần Accept trong 24h. */
    void sendExpertNewBookingEmail(
            String to,
            String expertName,
            Long bookingId,
            String bookingTypeLabel,
            long amountVnd,
            String ordersUrl);

    void sendTaskDeadlineEmail(
            String to,
            String subjectVi,
            String subjectEn,
            String assigneeName,
            String taskTitle,
            String workspaceTitle,
            String dueLabel,
            com.willa.ai.backend.entity.enums.TaskDeadlineNotificationType type,
            String taskUrl);
}
