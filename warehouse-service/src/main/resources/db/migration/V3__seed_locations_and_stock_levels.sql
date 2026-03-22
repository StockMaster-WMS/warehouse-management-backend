-- ============================================================
-- warehouse-service: V3__seed_locations_and_stock_levels.sql
-- Seed locations and stock levels for UI demo
-- ============================================================

INSERT INTO locations (
    id, warehouse_id, code, zone, aisle, rack, level, bin, location_type,
    max_weight_kg, max_volume_cm3, pick_sequence, status, is_active, created_at
)
VALUES
    ('0195b71b-7a01-7d91-85c8-5be26e6f2b01', '0195b71b-6f6c-7d91-85c8-5be26e6f2a11', 'A-01-01-01', 'A', '01', '01', 1, '01', 'STORAGE', 1200.00, 250000.0000, 10, 'AVAILABLE', TRUE, now() - interval '20 days'),
    ('0195b71b-7a01-7d91-85c8-5be26e6f2b02', '0195b71b-6f6c-7d91-85c8-5be26e6f2a11', 'A-01-01-02', 'A', '01', '01', 1, '02', 'STORAGE', 1200.00, 250000.0000, 20, 'AVAILABLE', TRUE, now() - interval '18 days'),
    ('0195b71b-7a01-7d91-85c8-5be26e6f2b03', '0195b71b-6f6c-7d91-85c8-5be26e6f2a13', 'B-02-03-01', 'B', '02', '03', 1, '01', 'PICKING', 800.00, 180000.0000, 30, 'AVAILABLE', TRUE, now() - interval '15 days'),
    ('0195b71b-7a01-7d91-85c8-5be26e6f2b04', '0195b71b-6f6c-7d91-85c8-5be26e6f2a15', 'C-01-02-01', 'C', '01', '02', 1, '01', 'STORAGE', 1000.00, 210000.0000, 15, 'MAINTENANCE', TRUE, now() - interval '10 days'),
    ('0195b71b-7a01-7d91-85c8-5be26e6f2b05', '0195b71b-6f6c-7d91-85c8-5be26e6f2a17', 'D-03-01-01', 'D', '03', '01', 1, '01', 'STAGING', 1500.00, 300000.0000, 40, 'AVAILABLE', TRUE, now() - interval '7 days')
ON CONFLICT (warehouse_id, code) DO UPDATE
SET
    zone = EXCLUDED.zone,
    aisle = EXCLUDED.aisle,
    rack = EXCLUDED.rack,
    level = EXCLUDED.level,
    bin = EXCLUDED.bin,
    location_type = EXCLUDED.location_type,
    max_weight_kg = EXCLUDED.max_weight_kg,
    max_volume_cm3 = EXCLUDED.max_volume_cm3,
    pick_sequence = EXCLUDED.pick_sequence,
    status = EXCLUDED.status,
    is_active = EXCLUDED.is_active;

INSERT INTO stock_levels (
    id, warehouse_id, location_id, product_id, lot_number, expiry_date,
    qty_on_hand, qty_reserved, updated_at
)
VALUES
    ('0195b71b-8b02-7d91-85c8-5be26e6f2c01', '0195b71b-6f6c-7d91-85c8-5be26e6f2a11', '0195b71b-7a01-7d91-85c8-5be26e6f2b01', '0195b71b-9c03-7d91-85c8-5be26e6f2d01', 'LOT-IPHONE16-2409', '2027-09-30', 520, 70, now() - interval '1 days'),
    ('0195b71b-8b02-7d91-85c8-5be26e6f2c02', '0195b71b-6f6c-7d91-85c8-5be26e6f2a11', '0195b71b-7a01-7d91-85c8-5be26e6f2b02', '0195b71b-9c03-7d91-85c8-5be26e6f2d02', 'LOT-SAMSUNG-2410', '2027-10-15', 430, 45, now() - interval '2 days'),
    ('0195b71b-8b02-7d91-85c8-5be26e6f2c03', '0195b71b-6f6c-7d91-85c8-5be26e6f2a13', '0195b71b-7a01-7d91-85c8-5be26e6f2b03', '0195b71b-9c03-7d91-85c8-5be26e6f2d03', 'LOT-LAPTOP-2408', '2028-08-31', 265, 35, now() - interval '1 days'),
    ('0195b71b-8b02-7d91-85c8-5be26e6f2c04', '0195b71b-6f6c-7d91-85c8-5be26e6f2a15', '0195b71b-7a01-7d91-85c8-5be26e6f2b04', '0195b71b-9c03-7d91-85c8-5be26e6f2d04', 'LOT-MOUSE-2411', NULL, 910, 120, now() - interval '3 days'),
    ('0195b71b-8b02-7d91-85c8-5be26e6f2c05', '0195b71b-6f6c-7d91-85c8-5be26e6f2a17', '0195b71b-7a01-7d91-85c8-5be26e6f2b05', '0195b71b-9c03-7d91-85c8-5be26e6f2d05', 'LOT-KEYBOARD-2412', NULL, 640, 52, now() - interval '1 days')
ON CONFLICT (location_id, product_id, lot_number) DO UPDATE
SET
    qty_on_hand = EXCLUDED.qty_on_hand,
    qty_reserved = EXCLUDED.qty_reserved,
    updated_at = EXCLUDED.updated_at;
