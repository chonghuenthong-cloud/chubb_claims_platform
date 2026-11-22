package com.chubb.claims.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Transactional outbox, publish side: every second, sends unpublished outbox rows to Kafka in order,
 * waits for Kafka to confirm each one, then marks it published.
 *
 * Delivery is at-least-once: if the app stops after sending but before marking, the row is sent again,
 * so consumers must ignore events they have already stored (they do, by event id).
 * If Kafka is down, sending fails, the transaction rolls back and the rows wait for the next run;
 * claim changes keep working in the meantime.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    private record Row(UUID id, UUID claimId, String payload) {
    }

    @Scheduled(fixedDelayString = "${claims.outbox.relay-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        // SKIP LOCKED: if several app instances run, each row is picked up by only one of them
        List<Row> rows = jdbc.query("""
                SELECT id, aggregate_id, payload::text AS payload
                FROM outbox_event
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new Row(rs.getObject("id", UUID.class),
                rs.getObject("aggregate_id", UUID.class), rs.getString("payload")));

        for (Row row : rows) {
            try {
                // Key = claim id: all events of one claim go to the same partition, so they stay in order
                kafka.send(KafkaTopics.CLAIM_EVENTS, row.claimId().toString(), row.payload()).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Stop here so later events of the same claim aren't published before this one
                log.warn("Kafka publish failed; {} event(s) will be retried on the next run", rows.size(), e);
                throw new IllegalStateException("Outbox publish failed", e);
            }
            jdbc.update("UPDATE outbox_event SET published_at = now() WHERE id = ?", row.id());
        }
    }
}
