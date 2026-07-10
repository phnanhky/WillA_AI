package com.willa.ai.backend.service;

public interface EmailService {

    void sendSimpleEmail(String to, String subject, String body);

    void sendHtmlEmail(String to, String subject, String htmlContent);

    void sendVerificationEmail(String to, String verificationLink);

    void sendPasswordResetEmail(String to, String resetLink);

    void sendWelcomeEmail(String to, String fullName);

    void sendWorkspaceInviteEmail(String to, String workspaceName, String inviterName, String inviteLink, String role);

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
