package com.willa.ai.backend.cron;

import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StudentCronTask {

    private final UserRepository userRepository;

    /**
     * Run every day at midnight to check expired student verifications.
     * If a student was verified more than 1 year ago, revoke their student status.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processExpiredStudentVerifications() {
        log.info("Running cron job: processExpiredStudentVerifications");
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        
        // Find all users who are students and verified more than 1 year ago
        List<User> expiredStudents = userRepository.findByIsStudentTrueAndStudentVerifiedAtBefore(oneYearAgo);
        
        for (User user : expiredStudents) {
            log.info("Student verification expired for user: {}", user.getEmail());
            user.setIsStudent(false);
            userRepository.save(user);
        }
    }
}
