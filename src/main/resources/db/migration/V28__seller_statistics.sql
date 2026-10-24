-- Lượt truy cập shop
CREATE TABLE shop_visit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id     UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    visitor_id  UUID,
    visited_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shop_visit_shop_date ON shop_visit_log (shop_id, visited_at);

-- View count cho product (conversion rate = orders / views)
ALTER TABLE product ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;

-- Index phục vụ thống kê doanh thu theo ngày/trạng thái
CREATE INDEX idx_orders_shop_status_created
    ON orders (shop_id, status, created_at);
