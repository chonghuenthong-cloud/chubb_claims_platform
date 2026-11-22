package com.chubb.claims.events;

import com.chubb.claims.claim.ClaimEventType;
import com.chubb.claims.claim.ClaimStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Locale;

/**
 * Consumer group "notifications": turns status changes into in-app notifications for the claimant.
 *
 * Rules (decided): every status change notifies the claimant, EXCEPT changes the claimant made themselves
 * (submitting, answering). Reserve revisions are internal and never notify.
 * Duplicate deliveries are ignored by event id.
 */
@Component
public class NotificationConsumer {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public NotificationConsumer(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = KafkaTopics.CLAIM_EVENTS, groupId = "notifications")
    public void onEvent(String payload) {
        ClaimEvent event = json.readValue(payload, ClaimEvent.class);
        String message = messageFor(event);
        if (message == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO notification (event_id, claimant_id, claim_id, claim_number, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                event.eventId(), event.claimantId(), event.claimId(), event.claimNumber(), message,
                Timestamp.from(event.occurredAt()));
    }

    /** The text the claimant sees, or null when this event doesn't notify them. */
    static String messageFor(ClaimEvent e) {
        boolean ownAction = e.actorId().equals(e.claimantId());
        if (e.type() != ClaimEventType.CLAIM_STATUS_CHANGED || ownAction) {
            return null;
        }
        String claim = e.claimNumber();
        return switch (e.toStatus()) {
            case UNDER_REVIEW -> e.fromStatus() == ClaimStatus.SUBMITTED ? e.actorName() + " picked up " + claim : null;
            case AWAITING_INFO -> "Question on " + claim + ": " + e.note();
            case APPROVED -> claim + " approved: " + money(e.currency(), e.approvedAmount());
            case REJECTED -> claim + " rejected: " + e.note();
            case SETTLED -> claim + " settled: " + money(e.currency(), e.approvedAmount()) + " paid";
            case SUBMITTED -> null;
        };
    }

    /** e.g. "SGD 3,800.00" */
    private static String money(String currency, BigDecimal amount) {
        return currency + " " + String.format(Locale.ENGLISH, "%,.2f", amount);
    }
}
