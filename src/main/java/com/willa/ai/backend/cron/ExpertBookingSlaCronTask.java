package com.willa.ai.backend.cron;

import com.willa.ai.backend.service.ExpertBookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpertBookingSlaCronTask {

    private final ExpertBookingService expertBookingService;

    /** Mỗi 15 phút: hết SLA Accept → hoàn tiền; hết Q&A REVIEW → auto complete. */
    @Scheduled(cron = "0 */15 * * * *")
    public void processExpertBookingSla() {
        int expired = expertBookingService.expireUnacceptedBookings();
        int completed = expertBookingService.autoCompleteExpiredReviewQa();
        int hourlyDone = expertBookingService.autoCompleteHourlyCallExhausted();
        if (expired > 0 || completed > 0 || hourlyDone > 0) {
            log.info("Expert booking SLA cron: expired={}, autoCompletedQa={}, hourlyExhausted={}",
                    expired, completed, hourlyDone);
        }
    }
}