CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION uuid_v7()
RETURNS uuid
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
    timestamp_hex text;
    random_hex text;
    variant_nibble text;
BEGIN
    timestamp_hex := lpad(to_hex(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0');
    random_hex := encode(gen_random_bytes(10), 'hex');
    variant_nibble := substr('89ab', (get_byte(gen_random_bytes(1), 0) % 4) + 1, 1);

    RETURN (
        substr(timestamp_hex, 1, 8) || '-' ||
        substr(timestamp_hex, 9, 4) || '-' ||
        '7' || substr(random_hex, 1, 3) || '-' ||
        variant_nibble || substr(random_hex, 4, 3) || '-' ||
        substr(random_hex, 7, 12)
    )::uuid;
END;
$$;

ALTER TABLE IF EXISTS users ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS token_blacklist ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS roles ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS permissions ALTER COLUMN id SET DEFAULT uuid_v7();

ALTER TABLE IF EXISTS categories ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS suppliers ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS products ALTER COLUMN id SET DEFAULT uuid_v7();

ALTER TABLE IF EXISTS warehouses ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS locations ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS stock_levels ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS stock_movements ALTER COLUMN id SET DEFAULT uuid_v7();

ALTER TABLE IF EXISTS audit_logs ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS purchase_orders ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS po_items ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS putaway_tasks ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS inbound_receipts ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS inbound_receipt_items ALTER COLUMN id SET DEFAULT uuid_v7();

ALTER TABLE IF EXISTS sales_orders ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS picking_items ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS sales_order_items ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS customers ALTER COLUMN id SET DEFAULT uuid_v7();

ALTER TABLE IF EXISTS cycle_counts ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS cycle_count_items ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS rma_headers ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS rma_items ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS notifications ALTER COLUMN id SET DEFAULT uuid_v7();
ALTER TABLE IF EXISTS ai_provider_configs ALTER COLUMN id SET DEFAULT uuid_v7();
