package com.notificationengine.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt  = LocalDateTime.now();
        this.updatedAt  = LocalDateTime.now();
        this.nextRetryAt = LocalDateTime.now(); // eligible immediately
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Domain helpers ─────────────────────────────────────

    public void incrementRetry(long baseDelaySeconds) {
        this.retryCount++;
        // Exponential backoff: baseDelay * 2^(retryCount-1)
        // Attempt 1 fail → wait 5s, 2 → 10s, 3 → 20s, 4 → 40s, 5 → DEAD
        long delaySeconds = (long) (baseDelaySeconds * Math.pow(2, this.retryCount - 1));
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        this.status = NotificationStatus.SCHEDULED;
    }

    public void markDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.nextRetryAt = null;
    }

    public void markDead() {
        this.status = NotificationStatus.DEAD;
        this.nextRetryAt = null;
    }

    public void markProcessing() {
        this.status = NotificationStatus.PROCESSING;
    }

    public enum Channel {
        EMAIL, SMS
    }

    public enum NotificationStatus {
        PENDING,     // just submitted
        PROCESSING,  // scheduler currently working on it
        SCHEDULED,   // failed, retry scheduled
        DELIVERED,   // success
        DEAD         // all retries exhausted
    }
}
