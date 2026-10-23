-- V15__admin_order_monitoring.sql
-- Module quản trị: giám sát đơn hàng toàn sàn, admin hủy can thiệp, ghi nhận ai hủy đơn.

ALTER TABLE orders
    ADD COLUMN cancelled_by TEXT CHECK (cancelled_by IN ('BUYER', 'SELLER', 'ADMIN'));

-- Đơn cũ: chỉ shop hủy mới nhập lý do, người mua hủy thì không.
UPDATE orders SET cancelled_by = 'SELLER' WHERE status = 'CANCELLED' AND cancel_reason IS NOT NULL;
UPDATE orders SET cancelled_by = 'BUYER' WHERE status = 'CANCELLED' AND cancel_reason IS NULL;

-- Danh sách toàn sàn mặc định mới đặt trước, lọc theo trạng thái dùng idx_orders_shop không được vì thiếu shop_id.
CREATE INDEX idx_orders_created ON orders (created_at DESC);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);
