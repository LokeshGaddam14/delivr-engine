package com.notificationengine.controller;

import com.notificationengine.dto.*;
import com.notificationengine.model.Notification.NotificationStatus;
import com.notificationengine.model.User;
import com.notificationengine.service.AuthService;
import com.notificationengine.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Submit, track, and manage notifications")
@SecurityRequirement(name = "Bearer Auth")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    // ── Submit ─────────────────────────────────────────────

    @PostMapping("/send")
    @Operation(
        summary = "Submit a notification",
        description = "Submits an EMAIL or SMS notification. It goes into the queue and the scheduler will process it within 30 seconds."
    )
    public ResponseEntity<ApiResponse<NotificationResponse>> send(
            @Valid @RequestBody SendNotificationRequest request,
            Authentication auth) {

        User user = authService.getUserByEmail(auth.getName());
        NotificationResponse response = notificationService.submit(request, user);
        return ResponseEntity.ok(ApiResponse.success("Notification queued successfully", response));
    }

    // ── Track ──────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID", description = "Returns current status, retry count, and next retry time")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(
            @Parameter(description = "Notification ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("OK", notificationService.getById(id)));
    }

    @GetMapping("/my")
    @Operation(summary = "My notifications", description = "Returns all notifications submitted by the logged-in user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> myNotifications(Authentication auth) {
        User user = authService.getUserByEmail(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("OK", notificationService.getMyNotifications(user.getId())));
    }

    @GetMapping("/{id}/attempts")
    @Operation(
        summary = "Get all delivery attempts for a notification",
        description = "Full audit trail — every send attempt with timestamp, success/failure, and error message"
    )
    public ResponseEntity<ApiResponse<List<AttemptResponse>>> getAttempts(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("OK", notificationService.getAttempts(id)));
    }

    // ── Admin ──────────────────────────────────────────────

    @GetMapping("/failed")
    @Operation(
        summary = "[ADMIN] Get all DEAD notifications",
        description = "Returns notifications that exhausted all 5 retry attempts. Requires ADMIN role."
    )
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getFailed() {
        return ResponseEntity.ok(
                ApiResponse.success("Dead notifications", notificationService.getByStatus(NotificationStatus.DEAD)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get notifications by status", description = "Filter by: PENDING, SCHEDULED, DELIVERED, DEAD, PROCESSING")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByStatus(
            @Parameter(description = "Status: PENDING | SCHEDULED | DELIVERED | DEAD")
            @PathVariable NotificationStatus status) {
        return ResponseEntity.ok(ApiResponse.success("OK", notificationService.getByStatus(status)));
    }

    @PostMapping("/{id}/retry")
    @Operation(
        summary = "[ADMIN] Manually retry a DEAD notification",
        description = "Resets a DEAD notification and immediately attempts delivery again. Requires ADMIN role."
    )
    public ResponseEntity<ApiResponse<NotificationResponse>> retry(@PathVariable Long id) {
        NotificationResponse response = notificationService.adminRetry(id);
        return ResponseEntity.ok(ApiResponse.success("Retry triggered", response));
    }

    @GetMapping("/analytics")
    @Operation(
        summary = "[ADMIN] Delivery analytics dashboard",
        description = "Success rate, retry stats, last-24h breakdown, average response time. Requires ADMIN role."
    )
    public ResponseEntity<ApiResponse<AnalyticsResponse>> analytics() {
        return ResponseEntity.ok(ApiResponse.success("Analytics", notificationService.getAnalytics()));
    }
}
