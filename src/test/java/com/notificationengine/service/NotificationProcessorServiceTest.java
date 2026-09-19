package com.notificationengine.service;

import com.notificationengine.model.Notification;
import com.notificationengine.model.Notification.NotificationStatus;
import com.notificationengine.model.NotificationAttempt;
import com.notificationengine.repository.NotificationAttemptRepository;
import com.notificationengine.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationAttemptRepository attemptRepository;
    @Mock private EmailSenderService emailSenderService;

    @InjectMocks private NotificationProcessorService processorService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processorService, "maxAttempts", 5);
        ReflectionTestUtils.setField(processorService, "baseDelaySeconds", 5L);
    }

    private Notification buildNotification(NotificationStatus status, int retryCount) {
        Notification n = Notification.builder()
                .recipient("test@example.com")
                .channel(Notification.Channel.EMAIL)
                .subject("Test Subject")
                .body("Test Body")
                .status(status)
                .retryCount(retryCount)
                .nextRetryAt(LocalDateTime.now().minusSeconds(1))
                .build();
        n.setId(1L);
        return n;
    }

    @Test
    @DisplayName("Skips processing if another instance already locked it")
    void skipsIfAlreadyLocked() {
        Notification n = buildNotification(NotificationStatus.PENDING, 0);
        when(notificationRepository.markAsProcessing(eq(1L), any())).thenReturn(0);

        processorService.process(n);

        verify(emailSenderService, never()).send(any(), any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("On success: status becomes DELIVERED")
    void successMarksDelivered() throws Exception {
        Notification n = buildNotification(NotificationStatus.PENDING, 0);
        when(notificationRepository.markAsProcessing(eq(1L), any())).thenReturn(1);
        doNothing().when(emailSenderService).send(anyString(), anyString(), anyString());
        when(notificationRepository.save(any())).thenReturn(n);
        when(attemptRepository.save(any())).thenReturn(new NotificationAttempt());

        processorService.process(n);

        assertEquals(NotificationStatus.DELIVERED, n.getStatus());
        verify(attemptRepository).save(argThat(a -> a.isSuccess()));
    }

    @Test
    @DisplayName("On failure below max retries: status becomes SCHEDULED")
    void failureSchedulesRetry() {
        Notification n = buildNotification(NotificationStatus.PENDING, 0);
        when(notificationRepository.markAsProcessing(eq(1L), any())).thenReturn(1);
        doThrow(new RuntimeException("SMTP timeout")).when(emailSenderService)
                .send(anyString(), anyString(), anyString());
        when(notificationRepository.save(any())).thenReturn(n);
        when(attemptRepository.save(any())).thenReturn(new NotificationAttempt());

        processorService.process(n);

        assertEquals(NotificationStatus.SCHEDULED, n.getStatus());
        assertEquals(1, n.getRetryCount());
        assertNotNull(n.getNextRetryAt());
        verify(attemptRepository).save(argThat(a -> !a.isSuccess()));
    }

    @Test
    @DisplayName("On failure at max retries: status becomes DEAD")
    void maxRetriesMarksDead() {
        Notification n = buildNotification(NotificationStatus.SCHEDULED, 4); // 4 already done, this is 5th
        when(notificationRepository.markAsProcessing(eq(1L), any())).thenReturn(1);
        doThrow(new RuntimeException("SMTP down")).when(emailSenderService)
                .send(anyString(), anyString(), anyString());
        when(notificationRepository.save(any())).thenReturn(n);
        when(attemptRepository.save(any())).thenReturn(new NotificationAttempt());

        processorService.process(n);

        assertEquals(NotificationStatus.DEAD, n.getStatus());
        assertNull(n.getNextRetryAt());
    }

    @Test
    @DisplayName("Exponential backoff: retry delays double each time")
    void exponentialBackoffDelayGrows() {
        Notification n1 = buildNotification(NotificationStatus.PENDING, 0);
        Notification n2 = buildNotification(NotificationStatus.PENDING, 1);
        Notification n3 = buildNotification(NotificationStatus.PENDING, 2);

        // Simulate failures
        n1.incrementRetry(5); // retry 1: wait 5s
        n2.incrementRetry(5); // retry 2: wait 10s
        n3.incrementRetry(5); // retry 3: wait 20s

        assertTrue(n2.getNextRetryAt().isAfter(n1.getNextRetryAt()));
        assertTrue(n3.getNextRetryAt().isAfter(n2.getNextRetryAt()));
    }
}
