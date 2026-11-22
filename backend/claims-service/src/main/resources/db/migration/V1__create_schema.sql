-- Core schema for the claims platform.
-- Enum-like columns are VARCHAR with CHECK constraints, so values stay readable in SQL and map to Java enums.

-- Users of both types: claimants and claims staff
CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CLAIMANT', 'STAFF')),
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- An insurance policy held by a claimant. Every claim is made against a policy.
CREATE TABLE policy (
    id             UUID PRIMARY KEY,
    policy_number  VARCHAR(30)    NOT NULL UNIQUE,
    holder_id      UUID           NOT NULL REFERENCES app_user (id),
    market         VARCHAR(2)     NOT NULL CHECK (market IN ('SG', 'MY', 'TH', 'HK', 'ID', 'PH')),
    product_type   VARCHAR(20)    NOT NULL CHECK (product_type IN ('MOTOR', 'PROPERTY')),
    coverage_limit NUMERIC(15, 2) NOT NULL CHECK (coverage_limit > 0),
    currency       VARCHAR(3)     NOT NULL,
    start_date     DATE           NOT NULL,
    end_date       DATE           NOT NULL,
    CHECK (end_date > start_date)
);

-- Human-readable claim numbers (CLM-000001), separate from the UUID primary key
CREATE SEQUENCE claim_number_seq START 1;

CREATE TABLE claim (
    id                   UUID PRIMARY KEY,
    claim_number         VARCHAR(20)    NOT NULL UNIQUE,
    policy_id            UUID           NOT NULL REFERENCES policy (id),
    claimant_id          UUID           NOT NULL REFERENCES app_user (id),
    assigned_officer_id  UUID           REFERENCES app_user (id),
    status               VARCHAR(20)    NOT NULL CHECK (status IN
                             ('SUBMITTED', 'UNDER_REVIEW', 'AWAITING_INFO', 'APPROVED', 'REJECTED', 'SETTLED')),
    incident_date        DATE           NOT NULL,
    incident_description TEXT           NOT NULL,
    estimated_loss       NUMERIC(15, 2) NOT NULL CHECK (estimated_loss >= 0),  -- claimant's estimate
    reserve_amount       NUMERIC(15, 2) NOT NULL CHECK (reserve_amount >= 0),  -- insurer's expected liability
    approved_amount      NUMERIC(15, 2) CHECK (approved_amount >= 0),
    rejection_reason     TEXT,
    submitted_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version              BIGINT         NOT NULL DEFAULT 0                     -- optimistic locking
);

-- Indexes for the two main access paths: a claimant's claims, and the staff work queue
CREATE INDEX idx_claim_claimant ON claim (claimant_id);
CREATE INDEX idx_claim_status_officer ON claim (status, assigned_officer_id);

-- Audit trail: one row per status change
CREATE TABLE claim_status_history (
    id          UUID PRIMARY KEY,
    claim_id    UUID        NOT NULL REFERENCES claim (id),
    from_status VARCHAR(20),                 -- NULL for the initial submission
    to_status   VARCHAR(20) NOT NULL,
    changed_by  UUID        NOT NULL REFERENCES app_user (id),
    note        TEXT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_history_claim ON claim_status_history (claim_id, changed_at);

-- Staff ask a question; the claimant answers. An unanswered request keeps the claim in AWAITING_INFO.
CREATE TABLE information_request (
    id           UUID PRIMARY KEY,
    claim_id     UUID        NOT NULL REFERENCES claim (id),
    requested_by UUID        NOT NULL REFERENCES app_user (id),
    question     TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    response     TEXT,
    responded_at TIMESTAMPTZ
);

CREATE INDEX idx_info_request_claim ON information_request (claim_id);
