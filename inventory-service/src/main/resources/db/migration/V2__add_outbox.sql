-- Transactional outbox — events committed atomically with inventory transactions
CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGSERIAL    PRIMARY KEY,
    event_id        VARCHAR(36)  NOT NULL UNIQUE,
    event_type      VARCHAR(50)  NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL DEFAULT 'INVENTORY_TRANSACTION',
    aggregate_id    VARCHAR(50)  NOT NULL,
    tenant_id       VARCHAR(50)  NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events (status, created_at);
