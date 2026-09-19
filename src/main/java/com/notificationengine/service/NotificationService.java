package com.notificationengine.service;

import com.notificationengine.dto.*;
import com.notificationengine.exception.ResourceNotFoundException;
import com.notificationengine.model.Notification;
import com.notificationengine.model.Notification.NotificationStatus;
import com.notificationengine.model.User;
import com.notificationengine.repository.NotificationAttemptRepository;
import com.notificationengine.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final NotificationProcessorService processorService;

    // ── Submit ─────────────────────────────────────────────

    @Transactional
    public NotificationResponse submit(SendNotificationRequest request, User user) {
        if (request.getChannel() == Notification.Channel.EMAIL && request.getSubject() == null) {
            request.setSubject("Notification");
        }

        Notification notification = Notification.builder()
                .recipient(request.getRecipient())
                .channel(request.getChannel())
                .subject(request.getSubject())
                .body(request.getBody())
                .submittedBy(user)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification submitted: id={} channel={} recipient={}",
                saved.getId(), saved.getChannel(), saved.getRecipient());

        return NotificationResponse.from(saved);
    }

    // ── Fetch ──────────────────────────────────────────────

    public NotificationResponse getById(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        return NotificationResponse.from(n);
    }

    public List<NotificationResponse> getByStatus(NotificationStatus status) {
        return notificationRepository.findByStatus(status)
                .stream().map(NotificationResponse::from).toList();
    }

    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findBySubmittedById(userId)
                .stream().map(NotificationResponse::from).toList();
    }

    public List<AttemptResponse> getAttempts(Long notificationId) {
        notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        return attemptRepository
                .findByNotificationIdOrderByAttemptNumberAsc(notificationId)
                .stream().map(AttemptResponse::from).toList();
    }

    // ── Admin: manual retry ────────────────────────────────

    @Transactional
    public NotificationResponse adminRetry(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));

        if (notification.getStatus() != NotificationStatus.DEAD) {
            throw new IllegalStateException("Only DEAD notifications can be manually retried. Current status: "
                    + notification.getStatus());
        }

        // Reset for one more attempt
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRetryCount(0);
        notification.setNextRetryAt(LocalDateTime.now());
        notificationRepository.save(notification);

        log.info("Admin manually reset notification id={} for retry", id);

        // Process immediately
        processorService.process(notification);

        Notification updated = notificationRepository.findById(id).orElseThrow();
        return NotificationResponse.from(updated);
    }

    // ── Analytics ──────────────────────────────────────────

    public AnalyticsResponse getAnalytics() {
        long total     = notificationRepository.count();
        long pending   = notificationRepository.countByStatus(NotificationStatus.PENDING);
        long scheduled = notificationRepository.countByStatus(NotificationStatus.SCHEDULED);
        long delivered = notificationRepository.countByStatus(NotificationStatus.DELIVERED);
        long dead      = notificationRepository.countByStatus(NotificationStatus.DEAD);

        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        long last24hTotal     = notificationRepository.countByDateRange(last24h, LocalDateTime.now());
        long last24hDelivered = notificationRepository.countByStatusAndDateRange(
                NotificationStatus.DELIVERED, last24h, LocalDateTime.now());
        long last24hFailed    = notificationRepository.countByStatusAndDateRange(
                NotificationStatus.DEAD, last24h, LocalDateTime.now());

        double successRate = total > 0 ? (delivered * 100.0 / total) : 0.0;
        Double avgRetries  = notificationRepository.avgRetriesForDelivered();
        Double avgResponse = attemptRepository.avgResponseTimeMs();

        return AnalyticsResponse.builder()
                .totalNotifications(total)
                .pending(pending)
                .scheduled(scheduled)
                .delivered(delivered)
                .dead(dead)
                .last24hTotal(last24hTotal)
                .last24hDelivered(last24hDelivered)
                .last24hFailed(last24hFailed)
                .deliverySuccessRatePercent(Math.round(successRate * 100.0) / 100.0)
                .avgRetriesOnDelivered(avgRetries != null ? Math.round(avgRetries * 100.0) / 100.0 : 0)
                .avgResponseTimeMs(avgResponse != null ? Math.round(avgResponse * 100.0) / 100.0 : 0)
                .build();
    }
}
