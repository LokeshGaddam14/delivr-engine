package com.notificationengine.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalyticsResponse {
    private long totalNotifications;
    private long pending;
    private long scheduled;
    private long delivered;
    private long dead;
    private long last24hTotal;
    private long last24hDelivered;
    private long last24hFailed;
    private double deliverySuccessRatePercent;
    private double avgRetriesOnDelivered;
    private double avgResponseTimeMs;
}
