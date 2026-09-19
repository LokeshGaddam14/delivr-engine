package com.notificationengine.repository;

import com.notificationengine.model.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {

    List<NotificationAttempt> findByNotificationIdOrderByAttemptNumberAsc(Long notificationId);

    long countBySuccessTrue();

    long countBySuccessFalse();

    @Query("""
            SELECT COUNT(a) FROM NotificationAttempt a
            WHERE a.success = false
            AND a.attemptedAt >= :from
            """)
    long countFailedAttemptsAfter(@Param("from") LocalDateTime from);

    @Query("""
            SELECT AVG(a.responseTimeMs) FROM NotificationAttempt a
            WHERE a.success = true
            AND a.responseTimeMs IS NOT NULL
            """)
    Double avgResponseTimeMs();
}
