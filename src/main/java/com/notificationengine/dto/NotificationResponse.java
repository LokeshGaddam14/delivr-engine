package com.notificationengine.dto;

import com.notificationengine.model.Notification;
import com.notificationengine.model.Notification.Channel;
import com.notificationengine.model.Notification.NotificationStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String recipient;
    private Channel channel;
    private String subject;
    private String body;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipient(n.getRecipient())
                .channel(n.getChannel())
                .subject(n.getSubject())
                .body(n.getBody())
                .status(n.getStatus())
                .retryCount(n.getRetryCount())
                .nextRetryAt(n.getNextRetryAt())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
