ALTER TABLE inbound_receipt_items
    ADD COLUMN IF NOT EXISTS location_id UUID;

UPDATE inbound_receipt_items iri
SET location_id = ir.location_id
FROM inbound_receipts ir
WHERE iri.receipt_id = ir.id
  AND iri.location_id IS NULL;

ALTER TABLE inbound_receipt_items
    ALTER COLUMN location_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inbound_receipt_items_location
    ON inbound_receipt_items(location_id);
