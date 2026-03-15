package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Override
    public void sendSimpleEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@willa.ai");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Simple email sent to: {}", to);
        } catch (Exception e) {
            log.error("Error sending simple email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("noreply@willa.ai");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("HTML email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Error sending HTML email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        String subject = "WillaAI - Email Verification";
        String htmlContent = buildVerificationEmailTemplate(verificationLink);
        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "WillaAI - Password Reset Request";
        String htmlContent = buildPasswordResetEmailTemplate(resetLink);
        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    public void sendWelcomeEmail(String to, String fullName) {
        String subject = "Welcome to WillaAI";
        String htmlContent = buildWelcomeEmailTemplate(fullName);
        sendHtmlEmail(to, subject, htmlContent);
    }

    private String buildVerificationEmailTemplate(String verificationLink) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; background-color: #f4f4f4; }" +
                ".container { max-width: 600px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; }" +
                ".header { color: #333; text-align: center; }" +
                ".content { color: #666; line-height: 1.6; }" +
                ".button { display: inline-block; background-color: #007bff; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; margin: 20px 0; }" +
                ".footer { color: #999; font-size: 12px; text-align: center; margin-top: 30px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'><h1>Verify Your Email</h1></div>" +
                "<div class='content'>" +
                "<p>Thank you for signing up with WillaAI!</p>" +
                "<p>Please click the button below to verify your email address:</p>" +
                "<a href='" + verificationLink + "' class='button'>Verify Email</a>" +
                "<p>If you didn't create this account, please ignore this email.</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>&copy; 2024 WillaAI. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String buildPasswordResetEmailTemplate(String resetLink) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; background-color: #f4f4f4; }" +
                ".container { max-width: 600px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; }" +
                ".header { color: #333; text-align: center; }" +
                ".content { color: #666; line-height: 1.6; }" +
                ".button { display: inline-block; background-color: #dc3545; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; margin: 20px 0; }" +
                ".footer { color: #999; font-size: 12px; text-align: center; margin-top: 30px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'><h1>Password Reset Request</h1></div>" +
                "<div class='content'>" +
                "<p>We received a request to reset your password.</p>" +
                "<p>Click the button below to create a new password:</p>" +
                "<a href='" + resetLink + "' class='button'>Reset Password</a>" +
                "<p>This link will expire in 1 hour.</p>" +
                "<p>If you didn't request a password reset, you can safely ignore this email.</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>&copy; 2024 WillaAI. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String buildWelcomeEmailTemplate(String fullName) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; background-color: #f4f4f4; }" +
                ".container { max-width: 600px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; }" +
                ".header { color: #333; text-align: center; }" +
                ".content { color: #666; line-height: 1.6; }" +
                ".footer { color: #999; font-size: 12px; text-align: center; margin-top: 30px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'><h1>Welcome to WillaAI</h1></div>" +
                "<div class='content'>" +
                "<p>Hello " + fullName + ",</p>" +
                "<p>Welcome to WillaAI! Your account has been successfully created.</p>" +
                "<p>You can now log in and start exploring our platform.</p>" +
                "<p>If you have any questions, feel free to contact our support team.</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>&copy; 2024 WillaAI. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
