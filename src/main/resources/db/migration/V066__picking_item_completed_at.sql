ALTER TABLE picking_items
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

UPDATE picking_items
SET completed_at = now()
WHERE status = 'PICKED'
  AND completed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_picking_items_completed_at
    ON picking_items(completed_at DESC);
