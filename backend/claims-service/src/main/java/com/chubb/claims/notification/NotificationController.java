package com.chubb.claims.notification;

import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.CurrentUserService;
import com.chubb.claims.user.Role;
import com.chubb.claims.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The claimant's notifications (the bell). Rows are created asynchronously by the Kafka NotificationConsumer;
 * this controller only reads them and marks them read. Claimant only, own notifications only.
 */
@RestController
public class NotificationController {

    private static final int LATEST = 20;

    /** The bell: unread count plus the latest notifications, newest first. */
    public record NotificationsResponse(long unreadCount, List<NotificationItem> items) {
    }

    public record NotificationItem(UUID id, UUID claimId, String claimNumber, String message,
                                   Instant createdAt, boolean read) {
    }

    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUser;

    public NotificationController(JdbcTemplate jdbc, CurrentUserService currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/me/notifications")
    public NotificationsResponse list(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId) {
        AppUser claimant = currentUser.require(userId, Role.CLAIMANT);
        Long unread = jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE claimant_id = ? AND read_at IS NULL", Long.class, claimant.getId());
        List<NotificationItem> items = jdbc.query("""
                SELECT id, claim_id, claim_number, message, created_at, read_at
                FROM notification WHERE claimant_id = ?
                ORDER BY created_at DESC LIMIT ?
                """, (rs, i) -> new NotificationItem(
                rs.getObject("id", UUID.class), rs.getObject("claim_id", UUID.class), rs.getString("claim_number"),
                rs.getString("message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") != null),
                claimant.getId(), LATEST);
        return new NotificationsResponse(unread == null ? 0 : unread, items);
    }

    /** Marks one notification read. Someone else's notification is reported as not found. */
    @PostMapping("/api/me/notifications/{id}/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId,
            @PathVariable UUID id) {
        AppUser claimant = currentUser.require(userId, Role.CLAIMANT);
        int updated = jdbc.update(
                "UPDATE notification SET read_at = coalesce(read_at, now()) WHERE id = ? AND claimant_id = ?",
                id, claimant.getId());
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/me/notifications/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId) {
        AppUser claimant = currentUser.require(userId, Role.CLAIMANT);
        jdbc.update("UPDATE notification SET read_at = now() WHERE claimant_id = ? AND read_at IS NULL", claimant.getId());
        return ResponseEntity.noContent().build();
    }
}
