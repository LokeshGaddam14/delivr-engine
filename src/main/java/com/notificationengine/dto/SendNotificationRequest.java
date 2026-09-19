package com.notificationengine.dto;

import com.notificationengine.model.Notification.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendNotificationRequest {

    @NotBlank(message = "Recipient is required")
    private String recipient;

    @NotNull(message = "Channel is required (EMAIL or SMS)")
    private Channel channel;

    private String subject;

    @NotBlank(message = "Body is required")
    private String body;
}
