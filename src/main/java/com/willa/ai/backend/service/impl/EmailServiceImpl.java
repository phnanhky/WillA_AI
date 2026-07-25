package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.entity.enums.TaskDeadlineNotificationType;
import com.willa.ai.backend.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    /** Brand purple — khớp broadcast / logo WILLA. */
    private static final String BRAND = "#943DFD";
    private static final String BRAND_SOFT = "#f6f1ff";
    private static final String TEXT = "#222222";
    private static final String MUTED = "#666666";
    private static final String FOOTER = "#999999";
    private static final int YEAR = 2026;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.frontendUrl:${app.baseUrl:https://willaai.tech}}")
    private String frontendUrl;

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
        String subject = "WillaAI - Xác minh email";
        String body =
                "<p style='margin:0 0 12px;'>Cảm ơn bạn đã đăng ký <strong style='color:" + BRAND + ";'>WillaAI</strong>.</p>"
                        + "<p style='margin:0 0 20px;'>Bấm nút bên dưới để xác minh địa chỉ email và kích hoạt tài khoản:</p>"
                        + ctaButton(verificationLink, "Xác minh email")
                        + "<p style='margin:20px 0 0;color:" + MUTED + ";font-size:13px;'>"
                        + "Nếu bạn không tạo tài khoản này, hãy bỏ qua email.</p>";
        sendHtmlEmail(to, subject, wrapEmail("Xác minh email", body));
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "WillaAI - Đặt lại mật khẩu";
        String body =
                "<p style='margin:0 0 12px;'>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>"
                        + "<p style='margin:0 0 20px;'>Bấm nút bên dưới để tạo mật khẩu mới:</p>"
                        + ctaButton(resetLink, "Đặt lại mật khẩu")
                        + "<p style='margin:20px 0 0;color:" + MUTED + ";font-size:13px;'>"
                        + "Link hết hạn sau 1 giờ. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>";
        sendHtmlEmail(to, subject, wrapEmail("Đặt lại mật khẩu", body));
    }

    @Override
    public void sendWelcomeEmail(String to, String fullName) {
        String name = fullName != null && !fullName.isBlank() ? escapeHtml(fullName.trim()) : "bạn";
        String loginUrl = appBase() + "/login";
        String subject = "Chào mừng đến với WillaAI";
        String body =
                "<p style='margin:0 0 12px;'>Xin chào <strong>" + name + "</strong>,</p>"
                        + "<p style='margin:0 0 12px;'>Chào mừng bạn đến với <strong style='color:" + BRAND
                        + ";'>WillaAI</strong> — nền tảng AI feedback thiết kế &amp; cộng tác dự án.</p>"
                        + "<p style='margin:0 0 20px;'>Tài khoản của bạn đã sẵn sàng. Đăng nhập để bắt đầu phân tích thiết kế, "
                        + "làm việc cùng workspace và kết nối Expert.</p>"
                        + ctaButton(loginUrl, "Bắt đầu với Willa")
                        + "<p style='margin:20px 0 0;color:" + MUTED + ";font-size:13px;'>"
                        + "Cần hỗ trợ? Liên hệ <a href='mailto:ewill.team@gmail.com' style='color:" + BRAND
                        + ";'>ewill.team@gmail.com</a>.</p>";
        sendHtmlEmail(to, subject, wrapEmail("Chào mừng đến WillaAI", body));
    }

    @Override
    public void sendTaskDeadlineEmail(
            String to,
            String subjectVi,
            String subjectEn,
            String assigneeName,
            String taskTitle,
            String workspaceTitle,
            String dueLabel,
            TaskDeadlineNotificationType type,
            String taskUrl) {
        String greeting = assigneeName != null && !assigneeName.isBlank() ? escapeHtml(assigneeName) : "bạn";
        String lead = type == TaskDeadlineNotificationType.ONE_DAY_BEFORE
                ? "Task của bạn sẽ đến hạn sau <strong>1 ngày</strong>."
                : "Task của bạn <strong>đã đến hạn</strong> ngay bây giờ.";
        String subject = "WillaAI - " + subjectVi;
        String body =
                "<p style='margin:0 0 12px;'>Xin chào " + greeting + ",</p>"
                        + "<p style='margin:0 0 12px;'>" + lead + "</p>"
                        + "<p style='margin:0 0 20px;'><strong>Task:</strong> " + escapeHtml(taskTitle) + "<br/>"
                        + "<strong>Workspace:</strong> " + escapeHtml(workspaceTitle) + "<br/>"
                        + "<strong>Deadline:</strong> " + escapeHtml(dueLabel) + "</p>"
                        + ctaButton(taskUrl, "Mở task")
                        + "<p style='margin:16px 0 0;color:" + MUTED + ";font-size:13px;'>Hoặc copy link: "
                        + escapeHtml(taskUrl) + "</p>";
        sendHtmlEmail(to, subject, wrapEmail(escapeHtml(subjectVi), body));
    }

    @Override
    public void sendExpertInviteEmail(String to, String fullName, String plainPassword, String loginUrl) {
        String name = fullName != null && !fullName.isBlank() ? fullName.trim() : "bạn";
        String subject = "WillaAI - Tài khoản Expert của bạn";
        String body =
                "<p style='margin:0 0 12px;'>Xin chào <strong>" + escapeHtml(name) + "</strong>,</p>"
                        + "<p style='margin:0 0 12px;'>Bạn đã được thêm làm <strong>Expert</strong> trên WillaAI. "
                        + "Dùng thông tin sau để đăng nhập và nhận booking từ khách:</p>"
                        + "<div style='background:" + BRAND_SOFT + ";border-radius:10px;padding:16px;margin:0 0 20px;'>"
                        + "<p style='margin:0 0 8px;'><strong>Email:</strong> " + escapeHtml(to) + "</p>"
                        + "<p style='margin:0;'><strong>Mật khẩu tạm:</strong> "
                        + "<code style='font-size:15px;color:" + BRAND + ";'>" + escapeHtml(plainPassword) + "</code></p>"
                        + "</div>"
                        + ctaButton(loginUrl, "Đăng nhập Willa")
                        + "<p style='margin:16px 0 0;color:" + MUTED + ";font-size:13px;'>"
                        + "Sau khi đăng nhập, mở <strong>Experts → Orders</strong> để xem booking và chat/gọi với khách.</p>"
                        + "<p style='margin:8px 0 0;color:" + FOOTER + ";font-size:12px;'>"
                        + "Nên đổi mật khẩu sau lần đăng nhập đầu.</p>";
        sendHtmlEmailOrThrow(to, subject, wrapEmail("Chào mừng Expert WillaAI", body));
    }

    @Override
    public void sendExpertAssignedEmail(String to, String fullName, String loginUrl) {
        String name = fullName != null && !fullName.isBlank() ? fullName.trim() : "bạn";
        String subject = "WillaAI - Bạn đã được thêm làm Expert";
        String body =
                "<p style='margin:0 0 12px;'>Xin chào <strong>" + escapeHtml(name) + "</strong>,</p>"
                        + "<p style='margin:0 0 20px;'>Tài khoản của bạn đã được gắn quyền <strong>Expert</strong> trên WillaAI. "
                        + "Đăng nhập bằng email/mật khẩu hiện tại, rồi vào <strong>Experts → Orders</strong> để nhận booking.</p>"
                        + ctaButton(loginUrl, "Đăng nhập Willa");
        sendHtmlEmailOrThrow(to, subject, wrapEmail("Expert WillaAI", body));
    }

    @Override
    public void sendExpertNewBookingEmail(
            String to,
            String expertName,
            Long bookingId,
            String bookingTypeLabel,
            long amountVnd,
            String ordersUrl) {
        String name = expertName != null && !expertName.isBlank() ? expertName.trim() : "bạn";
        String subject = "WillaAI - Đơn Expert #" + bookingId + " cần nhận trong 24h";
        String body =
                "<p style='margin:0 0 12px;'>Xin chào <strong>" + escapeHtml(name) + "</strong>,</p>"
                        + "<p style='margin:0 0 12px;'>Khách vừa thanh toán đơn <strong>"
                        + escapeHtml(bookingTypeLabel) + "</strong> (" + amountVnd + " VND).</p>"
                        + "<p style='margin:0 0 20px;'><strong>Bạn cần bấm Nhận đơn (Accept) trong 24 giờ</strong> — "
                        + "quá hạn hệ thống hủy đơn và yêu cầu hoàn tiền khách.</p>"
                        + ctaButton(ordersUrl, "Mở đơn hàng Expert");
        sendHtmlEmail(to, subject, wrapEmail("Đơn mới #" + bookingId, body));
    }

    @Override
    public void sendWorkspaceInviteEmail(String to, String workspaceName, String inviterName, String inviteLink, String role) {
        String subject = "WillaAI - Lời mời tham gia workspace \"" + workspaceName + "\"";
        String body =
                "<p style='margin:0 0 12px;'><strong>" + escapeHtml(inviterName)
                        + "</strong> đã mời bạn tham gia workspace <strong>"
                        + escapeHtml(workspaceName) + "</strong> với quyền <strong>"
                        + escapeHtml(role) + "</strong>.</p>"
                        + ctaButton(inviteLink, "Tham gia workspace")
                        + "<p style='margin:16px 0 0;color:" + MUTED + ";font-size:14px;line-height:1.5;'>"
                        + "Chưa có tài khoản? Bấm nút trên để <strong>tạo tài khoản miễn phí</strong> "
                        + "bằng đúng email <strong>" + escapeHtml(to) + "</strong>, xác minh email, rồi tự động vào workspace.</p>"
                        + "<p style='margin:8px 0 0;color:" + MUTED + ";font-size:13px;'>Hoặc copy link: "
                        + escapeHtml(inviteLink) + "</p>"
                        + "<p style='margin:8px 0 0;color:" + FOOTER + ";font-size:12px;'>Link hết hạn sau 7 ngày.</p>";
        sendHtmlEmailOrThrow(to, subject, wrapEmail("Lời mời workspace", body));
    }

    private void sendHtmlEmailOrThrow(String to, String subject, String htmlContent) {
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
            throw new RuntimeException("Không gửi được email tới " + to, e);
        }
    }

    private String appBase() {
        String base = frontendUrl != null ? frontendUrl.trim() : "https://willaai.tech";
        return base.replaceAll("/$", "");
    }

    /** CTA: nền tím brand, chữ trắng — inline để client mail không mất màu. */
    private static String ctaButton(String href, String label) {
        return "<p style='margin:24px 0;'>"
                + "<a href='" + href + "' "
                + "style='display:inline-block;background-color:" + BRAND + ";background:" + BRAND + ";"
                + "color:#ffffff !important;padding:14px 28px;text-decoration:none;border-radius:10px;"
                + "font-weight:800;font-size:15px;letter-spacing:0.2px;line-height:1.2;'>"
                + label
                + "</a></p>";
    }

    private static String wrapEmail(String title, String bodyHtml) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "</head>"
                + "<body style='margin:0;padding:0;background-color:#f3f3f5;'>"
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' "
                + "style='background-color:#f3f3f5;'><tr><td align='center' style='padding:24px 12px;'>"
                + "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' "
                + "style='width:100%;max-width:600px;background-color:#ffffff;border-collapse:collapse;'>"
                + "<tr><td align='center' style='padding:28px 24px 12px;"
                + "font-family:Arial,Helvetica,sans-serif;font-size:40px;font-weight:900;"
                + "line-height:1;color:" + BRAND + ";letter-spacing:-2px;'>W</td></tr>"
                + "<tr><td align='center' style='padding:0 24px 24px;'>"
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' "
                + "style='background-color:" + BRAND + ";'><tr>"
                + "<td align='center' style='padding:14px 16px;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:22px;font-weight:800;letter-spacing:3px;color:#ffffff;'>WILLA</td>"
                + "</tr></table></td></tr>"
                + "<tr><td style='padding:0 28px 8px;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:22px;font-weight:800;line-height:1.3;color:" + BRAND + ";'>"
                + title + "</td></tr>"
                + "<tr><td style='padding:12px 28px 8px;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:15px;line-height:1.65;color:" + TEXT + ";'>"
                + bodyHtml + "</td></tr>"
                + "<tr><td style='padding:28px 28px 32px;border-top:1px solid #eeeeee;"
                + "font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:1.5;color:" + FOOTER
                + ";text-align:center;'>"
                + "&copy; " + YEAR + " WillaAI / E-WILL. All rights reserved."
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String escapeHtml(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
