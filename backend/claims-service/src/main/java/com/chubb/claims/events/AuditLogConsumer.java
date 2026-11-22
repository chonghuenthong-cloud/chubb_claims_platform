package com.chubb.claims.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;

/**
 * Consumer group "audit-log": stores every claim event in the append-only claim_event_log table.
 * This includes RESERVE_REVISED, so reserve changes are audited (who, before, after, when).
 * Duplicate deliveries are ignored by event id.
 */
@Component
public class AuditLogConsumer {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditLogConsumer(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = KafkaTopics.CLAIM_EVENTS, groupId = "audit-log")
    public void onEvent(String payload) {
        ClaimEvent event = json.readValue(payload, ClaimEvent.class);
        jdbc.update("""
                INSERT INTO claim_event_log (event_id, claim_id, event_type, occurred_at, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (event_id) DO NOTHING
                """,
                event.eventId(), event.claimId(), event.type().name(), Timestamp.from(event.occurredAt()), payload);
    }
}
