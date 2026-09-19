package com.notificationengine.dto;

import com.notificationengine.model.NotificationAttempt;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AttemptResponse {
    private Long id;
    private int attemptNumber;
    private LocalDateTime attemptedAt;
    private boolean success;
    private String failureReason;
    private Long responseTimeMs;

    public static AttemptResponse from(NotificationAttempt a) {
        return AttemptResponse.builder()
                .id(a.getId())
                .attemptNumber(a.getAttemptNumber())
                .attemptedAt(a.getAttemptedAt())
                .success(a.isSuccess())
                .failureReason(a.getFailureReason())
                .responseTimeMs(a.getResponseTimeMs())
                .build();
    }
}
