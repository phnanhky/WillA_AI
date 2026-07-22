package com.willa.ai.backend.config;

import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One-off: gửi mail ưu đãi 90% ({@code classpath:mail/broadcast-first-purchase-90.html})
 * cho toàn bộ user khi backend start.
 * <p>
 * Deploy 1 lần → xem log {@code Broadcast mail done} →
 * <b>xóa {@code @Component} hoặc cả class này</b> rồi deploy lại (tránh gửi trùng mỗi lần restart).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BroadcastMailInitializer implements CommandLineRunner {

    private static final String HTML_RESOURCE = "mail/broadcast-first-purchase-90.html";
    private static final String SUBJECT = "WILLA — Ưu đãi 90% dành cho tài khoản mua gói lần đầu";
    /** Delay giữa các mail (ms) — tránh Gmail SMTP rate-limit. */
    private static final long DELAY_MS = 400L;

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@willa.ai}")
    private String mailFrom;

    @Override
    public void run(String... args) {
        final String html;
        try {
            html = new String(
                    new ClassPathResource(HTML_RESOURCE).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Broadcast mail: cannot load {}", HTML_RESOURCE, e);
            return;
        }

        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .filter(User::isEnabled)
                .toList();

        log.info("Broadcast mail: sending to {} users…", users.size());
        int ok = 0;
        int fail = 0;
        for (User user : users) {
            String to = user.getEmail().trim();
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailFrom);
                helper.setTo(to);
                helper.setSubject(SUBJECT);
                helper.setText(html, true);
                mailSender.send(message);
                ok++;
                log.info("Broadcast mail OK ({}/{}) → {}", ok + fail, users.size(), to);
            } catch (Exception e) {
                fail++;
                log.error("Broadcast mail FAIL → {}: {}", to, e.getMessage());
            }
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Broadcast mail interrupted after {} sent", ok);
                break;
            }
        }
        log.info("Broadcast mail done. ok={}, fail={}, total={}", ok, fail, users.size());
        log.warn(">>> Xóa @Component trên BroadcastMailInitializer rồi deploy lại để không gửi trùng.");
    }
}
