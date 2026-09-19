package com.notificationengine.service;

import com.notificationengine.model.Notification;
import com.notificationengine.model.Notification.NotificationStatus;
import com.notificationengine.model.NotificationAttempt;
import com.notificationengine.repository.NotificationAttemptRepository;
import com.notificationengine.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core retry + delivery engine.
 *
 * Responsibilities:
 * 1. Attempt to send a notification
 * 2. Log the attempt (success or failure) to notification_attempts
 * 3. On failure: schedule retry with exponential backoff
 * 4. On max retries reached: mark DEAD
 * 5. On success: mark DELIVERED
 *
 * This is the class you explain in interviews.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessorService {

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final EmailSenderService emailSenderService;

    @Value("${notification.retry.max-attempts:5}")
    private int maxAttempts;

    @Value("${notification.retry.base-delay-seconds:5}")
    private long baseDelaySeconds;

    /**
     * Main entry point called by the scheduler.
     * Processes ONE notification safely.
     */
    @Transactional
    public void process(Notification notification) {
        // Atomic lock — prevents concurrent schedulers from double-processing
        int locked = notificationRepository.markAsProcessing(notification.getId(), LocalDateTime.now());
        if (locked == 0) {
            log.warn("Notification {} already being processed by another instance — skipping", notification.getId());
            return;
        }

        log.info("Processing notification id={} attempt={} channel={}",
                notification.getId(), notification.getRetryCount() + 1, notification.getChannel());

        long startTime = System.currentTimeMillis();
        boolean success = false;
        String failureReason = null;

        try {
            switch (notification.getChannel()) {
                case EMAIL -> emailSenderService.send(
                        notification.getRecipient(),
                        notification.getSubject(),
                        notification.getBody()
                );
                case SMS -> simulateSms(notification.getRecipient(), notification.getBody());
            }
            success = true;

        } catch (Exception e) {
            failureReason = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("Notification id={} failed on attempt {}: {}",
                    notification.getId(), notification.getRetryCount() + 1, failureReason);
        }

        long responseTime = System.currentTimeMillis() - startTime;

        // ── Log this attempt ───────────────────────────────
        NotificationAttempt attempt = NotificationAttempt.builder()
                .notification(notification)
                .attemptNumber(notification.getRetryCount() + 1)
                .success(success)
                .failureReason(failureReason)
                .responseTimeMs(responseTime)
                .build();
        attemptRepository.save(attempt);

        // ── Update notification state ──────────────────────
        if (success) {
            notification.markDelivered();
            log.info("Notification id={} DELIVERED successfully", notification.getId());

        } else {
            int nextRetryCount = notification.getRetryCount() + 1;

            if (nextRetryCount >= maxAttempts) {
                notification.markDead();
                log.error("Notification id={} is DEAD after {} attempts. Manual intervention needed.",
                        notification.getId(), maxAttempts);
            } else {
                notification.incrementRetry(baseDelaySeconds);
                log.info("Notification id={} scheduled for retry #{} at {}",
                        notification.getId(), nextRetryCount + 1, notification.getNextRetryAt());
            }
        }

        notificationRepository.save(notification);
    }

    /**
     * SMS is simulated since we don't have a real SMS gateway.
     * In production: call Twilio / MSG91 API here.
     * For now: throws exception 30% of the time to simulate failures.
     */
    private void simulateSms(String phone, String body) {
        if (Math.random() < 0.3) {
            throw new RuntimeException("SMS Gateway timeout — simulated failure");
        }
        log.info("SMS simulated-sent to: {} | body: {}", phone, body.substring(0, Math.min(30, body.length())));
    }
}
