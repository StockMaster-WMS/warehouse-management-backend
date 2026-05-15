-- Seed product records referenced by the warehouse stock demo data.
-- Stock levels store product_id as a cross-service reference, so this keeps AI/report joins useful on fresh databases.

INSERT INTO categories (id, code, name, path, level, is_active, created_at)
VALUES
    ('0195b71b-9b01-7d91-85c8-5be26e6f2d00', 'CAT-DEMO-ELECTRONICS', 'Demo Electronics', 'Demo Electronics', 0, TRUE, now())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

INSERT INTO products (
    id, sku, barcode_ean13, name, category_id, primary_supplier_id, base_unit,
    weight_kg, volume_cm3, min_stock_qty, is_lot_tracked, is_expiry_tracked,
    is_frozen, is_fragile, is_hazmat, is_heavy, status, created_at, updated_at, created_by
)
VALUES
    ('0195b71b-9c03-7d91-85c8-5be26e6f2d01', 'IPH-16-PROMAX', '8938500000011', 'iPhone 16 Pro Max 256GB', '0195b71b-9b01-7d91-85c8-5be26e6f2d00', NULL, 'PCS', 0.2300, 850.0000, 120, TRUE, FALSE, FALSE, TRUE, FALSE, FALSE, 'ACTIVE', now(), now(), '00000000-0000-0000-0000-000000000000'),
    ('0195b71b-9c03-7d91-85c8-5be26e6f2d02', 'TV-SAMSUNG-55', '8938500000028', 'Tivi Samsung 55 inch OLED', '0195b71b-9b01-7d91-85c8-5be26e6f2d00', NULL, 'PCS', 18.5000, 125000.0000, 80, TRUE, FALSE, FALSE, TRUE, FALSE, TRUE, 'ACTIVE', now(), now(), '00000000-0000-0000-0000-000000000000'),
    ('0195b71b-9c03-7d91-85c8-5be26e6f2d03', 'LAP-DELL-XPS13', '8938500000035', 'Laptop Dell XPS 13', '0195b71b-9b01-7d91-85c8-5be26e6f2d00', NULL, 'PCS', 1.2000, 4500.0000, 60, TRUE, FALSE, FALSE, TRUE, FALSE, FALSE, 'ACTIVE', now(), now(), '00000000-0000-0000-0000-000000000000'),
    ('0195b71b-9c03-7d91-85c8-5be26e6f2d04', 'MOUSE-LOGI-MX3', '8938500000042', 'Chuột Logitech MX Master 3S', '0195b71b-9b01-7d91-85c8-5be26e6f2d00', NULL, 'PCS', 0.1400, 650.0000, 150, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, 'ACTIVE', now(), now(), '00000000-0000-0000-0000-000000000000'),
    ('0195b71b-9c03-7d91-85c8-5be26e6f2d05', 'KEY-MECH-K2', '8938500000059', 'Bàn phím cơ Keychron K2', '0195b71b-9b01-7d91-85c8-5be26e6f2d00', NULL, 'PCS', 0.8000, 3200.0000, 100, TRUE, FALSE, FALSE, TRUE, FALSE, FALSE, 'ACTIVE', now(), now(), '00000000-0000-0000-0000-000000000000')
ON CONFLICT (sku) DO UPDATE
SET name = EXCLUDED.name,
    category_id = EXCLUDED.category_id,
    base_unit = EXCLUDED.base_unit,
    weight_kg = EXCLUDED.weight_kg,
    volume_cm3 = EXCLUDED.volume_cm3,
    min_stock_qty = EXCLUDED.min_stock_qty,
    is_lot_tracked = EXCLUDED.is_lot_tracked,
    is_expiry_tracked = EXCLUDED.is_expiry_tracked,
    is_frozen = EXCLUDED.is_frozen,
    is_fragile = EXCLUDED.is_fragile,
    is_hazmat = EXCLUDED.is_hazmat,
    is_heavy = EXCLUDED.is_heavy,
    status = EXCLUDED.status,
    updated_at = now();
