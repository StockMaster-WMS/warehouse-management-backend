ALTER TABLE inbound_receipts
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160),
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inbound_receipts_idempotency_key
    ON inbound_receipts(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
