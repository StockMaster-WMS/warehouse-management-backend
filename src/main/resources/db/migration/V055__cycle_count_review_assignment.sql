ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS assigned_to UUID;
ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;
ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;
ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS rejected_by UUID;
ALTER TABLE cycle_counts ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);

UPDATE cycle_counts
SET status = 'PENDING_REVIEW',
    submitted_at = COALESCE(completed_at, started_at, created_at)
WHERE status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_cc_assigned_to ON cycle_counts(assigned_to);
CREATE INDEX IF NOT EXISTS idx_cc_submitted_at ON cycle_counts(submitted_at);
