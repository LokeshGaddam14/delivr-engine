package com.notificationengine.scheduler;

import com.notificationengine.model.Notification;
import com.notificationengine.repository.NotificationRepository;
import com.notificationengine.service.NotificationProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The heartbeat of the system.
 *
 * Runs every 30 seconds. Finds all notifications that are:
 * - PENDING (never tried)
 * - SCHEDULED (failed, retry window has passed)
 *
 * Hands each one to NotificationProcessorService for delivery.
 *
 * Production-safe: DB-level locking prevents double-processing
 * even if multiple instances of this service run simultaneously.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessorService processorService;

    @Scheduled(fixedRateString = "${notification.scheduler.fixed-rate-ms:30000}")
    public void processQueue() {
        List<Notification> due = notificationRepository
                .findNotificationsReadyForProcessing(LocalDateTime.now());

        if (due.isEmpty()) {
            log.debug("Scheduler: no notifications due for processing");
            return;
        }

        log.info("Scheduler: found {} notification(s) ready for processing", due.size());

        for (Notification notification : due) {
            try {
                processorService.process(notification);
            } catch (Exception e) {
                // One failure must never stop others from processing
                log.error("Scheduler: unexpected error processing notification id={}: {}",
                        notification.getId(), e.getMessage(), e);
            }
        }
    }
}
