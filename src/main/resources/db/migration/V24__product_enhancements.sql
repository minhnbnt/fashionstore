-- V24__product_enhancements.sql
-- Đặc tả 2.2.1: Thêm sản phẩm mới kèm video, ảnh phẳng Virtual Try-On, thông tin bổ sung,
-- thông số vận chuyển và trạng thái kiểm duyệt PENDING.

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS video_key TEXT,
    ADD COLUMN IF NOT EXISTS garment_key TEXT,
    ADD COLUMN IF NOT EXISTS try_on_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS material TEXT,
    ADD COLUMN IF NOT EXISTS origin TEXT,
    ADD COLUMN IF NOT EXISTS care_instructions TEXT,
    ADD COLUMN IF NOT EXISTS size_guide TEXT,
    ADD COLUMN IF NOT EXISTS weight_grams INT,
    ADD COLUMN IF NOT EXISTS length_cm INT,
    ADD COLUMN IF NOT EXISTS width_cm INT,
    ADD COLUMN IF NOT EXISTS height_cm INT;

-- Cập nhật ràng buộc trạng thái sản phẩm, bổ sung PENDING (chờ admin duyệt)
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_status_check;
ALTER TABLE product
    ADD CONSTRAINT product_status_check
    CHECK (status IN ('PENDING', 'ACTIVE', 'HIDDEN', 'DELETED', 'BANNED'));

-- Bổ sung cờ is_trusted cho shop để tự động bỏ qua kiểm duyệt sản phẩm nếu là shop uy tín
ALTER TABLE shop
    ADD COLUMN IF NOT EXISTS is_trusted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_product_pending_approval
    ON product (status, created_at DESC)
    WHERE status = 'PENDING';
