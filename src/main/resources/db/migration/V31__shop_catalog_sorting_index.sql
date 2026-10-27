-- Trang shop catalog sắp xếp mặc định theo created_at với điều kiện
-- shop_id + status; index cũ (shop_id, status) thiếu created_at nên mỗi
-- request vẫn phải sort toàn bộ sản phẩm ACTIVE của shop.
CREATE INDEX IF NOT EXISTS idx_product_shop_status_created
    ON product (shop_id, status, created_at DESC);

-- Bị prefix bao trọn bởi index mới, giữ lại chỉ tốn phí maintain khi ghi.
DROP INDEX IF EXISTS idx_product_shop_status;
