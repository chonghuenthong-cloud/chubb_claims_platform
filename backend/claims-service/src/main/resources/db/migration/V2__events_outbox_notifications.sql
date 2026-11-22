-- Claim events: transactional outbox, audit log and claimant notifications.

-- Outbox: an event row is written in the SAME transaction as the claim change, so an event exists
-- if and only if the change was committed. OutboxRelay publishes unpublished rows to Kafka.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,               -- the event id
    aggregate_id UUID        NOT NULL,           -- claim id; also the Kafka message key
    event_type   VARCHAR(40) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ                     -- NULL until Kafka has confirmed it
);

-- The relay's query: oldest unpublished first
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

-- Audit log: every event, as received from Kafka. Append-only.
-- The event id as primary key makes duplicate deliveries harmless (insert is skipped).
CREATE TABLE claim_event_log (
    event_id    UUID PRIMARY KEY,
    claim_id    UUID        NOT NULL,
    event_type  VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload     JSONB       NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_log_claim ON claim_event_log (claim_id, occurred_at);

-- Claimant notifications, created by the notification consumer.
-- event_id is unique, so a duplicate delivery doesn't create a second notification.
CREATE TABLE notification (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID        NOT NULL UNIQUE,
    claimant_id  UUID        NOT NULL REFERENCES app_user (id),
    claim_id     UUID        NOT NULL REFERENCES claim (id),
    claim_number VARCHAR(20) NOT NULL,
    message      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    read_at      TIMESTAMPTZ                     -- NULL while unread
);

CREATE INDEX idx_notification_claimant ON notification (claimant_id, created_at DESC);
