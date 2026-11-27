-- V51__product_garment_images.sql
-- Cho phép người bán tải lên nhiều ảnh Garment cho một sản phẩm (Virtual Try-On).
-- Cột upload_id tham chiếu bảng upload (không ON DELETE CASCADE) theo thiết kế D6
-- để consumer upload_gc không xóa nhầm file S3 đang gắn với sản phẩm.

CREATE TABLE product_garment (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    upload_id UUID NOT NULL REFERENCES upload (id),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_garment_product_sort
    ON product_garment (product_id, sort_order ASC);

-- Backfill dữ liệu garment_upload_id hiện có trên bảng product sang product_garment
INSERT INTO product_garment (id, product_id, upload_id, sort_order, created_at)
SELECT gen_random_uuid(), id, garment_upload_id, 0, updated_at
FROM product
WHERE garment_upload_id IS NOT NULL;
