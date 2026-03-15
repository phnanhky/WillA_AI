package com.willa.ai.backend.service;

public interface EmailService {

    void sendSimpleEmail(String to, String subject, String body);

    void sendHtmlEmail(String to, String subject, String htmlContent);

    void sendVerificationEmail(String to, String verificationLink);

    void sendPasswordResetEmail(String to, String resetLink);

    void sendWelcomeEmail(String to, String fullName);
}
