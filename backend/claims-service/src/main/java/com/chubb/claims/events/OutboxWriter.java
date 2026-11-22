package com.chubb.claims.events;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.PendingClaimEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;

/**
 * Transactional outbox, write side: saves a claim's recorded events as outbox rows in the CALLER's transaction.
 * The claim change and its events are therefore committed together or not at all: no lost events if Kafka is
 * down, and no events for changes that rolled back. OutboxRelay publishes the rows afterwards.
 */
@Component
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Must run inside the transaction that changed the claim, after the claim has been flushed (so it has an id). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void store(Claim claim) {
        for (PendingClaimEvent pending : claim.pullPendingEvents()) {
            ClaimEvent event = ClaimEvent.of(claim, pending);
            jdbc.update("""
                    INSERT INTO outbox_event (id, aggregate_id, event_type, payload, created_at)
                    VALUES (?, ?, ?, ?::jsonb, ?)
                    """,
                    event.eventId(), event.claimId(), event.type().name(),
                    json.writeValueAsString(event), Timestamp.from(event.occurredAt()));
        }
    }
}
