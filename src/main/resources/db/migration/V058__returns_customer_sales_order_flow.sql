ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS customer_id UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS customer_id UUID;
ALTER TABLE rma_items ADD COLUMN IF NOT EXISTS sales_order_item_id UUID;

CREATE INDEX IF NOT EXISTS idx_sales_orders_customer_id ON sales_orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_orders_customer_name_status ON sales_orders(LOWER(customer_name), status);
CREATE INDEX IF NOT EXISTS idx_rma_headers_customer_id ON rma_headers(customer_id);
CREATE INDEX IF NOT EXISTS idx_rma_items_sales_order_item_id ON rma_items(sales_order_item_id);
