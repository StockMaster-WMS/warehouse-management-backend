-- Canonicalize stock rows that represent "no lot" so the unique constraint
-- cannot be bypassed by NULL lot_number values.
UPDATE stock_levels
SET lot_number = ''
WHERE lot_number IS NULL;

WITH duplicate_groups AS (
    SELECT
        location_id,
        product_id,
        lot_number,
        SUM(qty_on_hand) AS merged_qty_on_hand,
        SUM(COALESCE(qty_reserved, 0)) AS merged_qty_reserved,
        MIN(expiry_date) AS merged_expiry_date,
        MAX(updated_at) AS merged_updated_at
    FROM stock_levels
    GROUP BY location_id, product_id, lot_number
    HAVING COUNT(*) > 1
),
ranked_duplicates AS (
    SELECT
        s.id,
        dg.merged_qty_on_hand,
        dg.merged_qty_reserved,
        dg.merged_expiry_date,
        dg.merged_updated_at,
        ROW_NUMBER() OVER (
            PARTITION BY s.location_id, s.product_id, s.lot_number
            ORDER BY s.updated_at DESC NULLS LAST, s.id
        ) AS rn
    FROM stock_levels s
    JOIN duplicate_groups dg
      ON dg.location_id = s.location_id
     AND dg.product_id = s.product_id
     AND dg.lot_number = s.lot_number
)
UPDATE stock_levels s
SET qty_on_hand = rd.merged_qty_on_hand,
    qty_reserved = rd.merged_qty_reserved,
    expiry_date = rd.merged_expiry_date,
    updated_at = COALESCE(rd.merged_updated_at, now())
FROM ranked_duplicates rd
WHERE s.id = rd.id
  AND rd.rn = 1;

WITH ranked_duplicates AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY location_id, product_id, lot_number
            ORDER BY updated_at DESC NULLS LAST, id
        ) AS rn
    FROM stock_levels
)
DELETE FROM stock_levels s
USING ranked_duplicates rd
WHERE s.id = rd.id
  AND rd.rn > 1;

ALTER TABLE stock_levels
    ALTER COLUMN lot_number SET DEFAULT '',
    ALTER COLUMN lot_number SET NOT NULL;
