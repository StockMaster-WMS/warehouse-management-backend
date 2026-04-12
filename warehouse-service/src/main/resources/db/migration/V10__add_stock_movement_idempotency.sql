ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(512);

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_movements_idempotency_key
    ON stock_movements(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
