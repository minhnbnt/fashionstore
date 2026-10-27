-- V31__order_auto_cancel_system.sql
-- Cho phép SYSTEM trong cột cancelled_by của bảng orders
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_cancelled_by_check;
ALTER TABLE orders ADD CONSTRAINT orders_cancelled_by_check 
    CHECK (cancelled_by IN ('BUYER', 'SELLER', 'ADMIN', 'SYSTEM'));

-- Index hỗ trợ background job quét đơn PENDING theo thời gian tạo
CREATE INDEX IF NOT EXISTS idx_orders_pending_created ON orders (created_at) WHERE status = 'PENDING';
