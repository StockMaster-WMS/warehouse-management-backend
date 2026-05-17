ALTER TABLE picking_items
ADD COLUMN IF NOT EXISTS assignee_id UUID;

CREATE INDEX IF NOT EXISTS idx_pick_items_assignee ON picking_items(assignee_id);
