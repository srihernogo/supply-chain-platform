-- ============================================================
-- Blockchain Ledger Service — Flyway Migration V1
-- Runs for EACH tenant schema (toyota, honda, mitsubishi, etc.)
-- ============================================================

CREATE TABLE IF NOT EXISTS blockchain_blocks (
    id             BIGSERIAL    PRIMARY KEY,
    block_index    BIGINT       NOT NULL,
    timestamp      TIMESTAMP    NOT NULL,
    payload        VARCHAR(2000) NOT NULL,
    previous_hash  VARCHAR(64)  NOT NULL,
    hash           VARCHAR(64)  NOT NULL UNIQUE,
    transaction_no VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_block_index ON blockchain_blocks(block_index);
CREATE INDEX IF NOT EXISTS idx_block_hash ON blockchain_blocks(hash);
CREATE INDEX IF NOT EXISTS idx_block_trx_no ON blockchain_blocks(transaction_no);

-- Table to track processed event IDs for deduplication
CREATE TABLE IF NOT EXISTS processed_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(100) NOT NULL UNIQUE,
    processed_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_event_id ON processed_events(event_id);
