ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS count_number VARCHAR(40);
ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS scope VARCHAR(20);
ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS scope_value VARCHAR(255);
ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS idx_cc_count_number ON cycle_counts(count_number);
