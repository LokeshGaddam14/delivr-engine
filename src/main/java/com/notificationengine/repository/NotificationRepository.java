package com.notificationengine.repository;

import com.notificationengine.model.Notification;
import com.notificationengine.model.Notification.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ── Scheduler queries ──────────────────────────────────

    /**
     * Finds all notifications that are ready to be processed:
     * - PENDING (never tried) OR SCHEDULED (failed, retry window has passed)
     * - nextRetryAt is in the past (it's time to retry)
     * Used by the RetryScheduler every 30 seconds.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.status IN ('PENDING', 'SCHEDULED')
            AND n.nextRetryAt <= :now
            ORDER BY n.nextRetryAt ASC
            """)
    List<Notification> findNotificationsReadyForProcessing(@Param("now") LocalDateTime now);

    // ── Status-based queries ───────────────────────────────

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByStatusAndCreatedAtAfter(
            NotificationStatus status,
            LocalDateTime after
    );

    List<Notification> findBySubmittedById(Long userId);

    // ── Analytics queries ──────────────────────────────────

    long countByStatus(NotificationStatus status);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.createdAt >= :from AND n.createdAt <= :to
            """)
    long countByDateRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.status = :status
            AND n.createdAt >= :from AND n.createdAt <= :to
            """)
    long countByStatusAndDateRange(
            @Param("status") NotificationStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT AVG(n.retryCount) FROM Notification n
            WHERE n.status = 'DELIVERED'
            AND n.retryCount > 0
            """)
    Double avgRetriesForDelivered();

    // ── Lock for concurrent scheduler safety ──────────────

    /**
     * Atomically marks a notification as PROCESSING.
     * Prevents two scheduler instances from picking the same notification.
     */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.status = 'PROCESSING', n.updatedAt = :now
            WHERE n.id = :id
            AND n.status IN ('PENDING', 'SCHEDULED')
            """)
    int markAsProcessing(@Param("id") Long id, @Param("now") LocalDateTime now);
}
