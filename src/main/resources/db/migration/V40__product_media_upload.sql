-- V40__product_media_upload.sql
-- 3 cột file của domain sản phẩm (product_image.s3_key, product.video_key,
-- product.garment_key) tham chiếu bảng upload qua FK (design D6): thêm cột,
-- backfill join theo s3_key (V37 đã backfill đủ upload từ key legacy), rồi
-- drop cột cũ. FK không ON DELETE — xoá upload đang được tham chiếu sẽ nổ
-- constraint ("vệ sĩ" cho consumer GC).

ALTER TABLE product_image ADD COLUMN upload_id UUID REFERENCES upload (id);
ALTER TABLE product ADD COLUMN video_upload_id UUID REFERENCES upload (id);
ALTER TABLE product ADD COLUMN garment_upload_id UUID REFERENCES upload (id);

UPDATE product_image SET upload_id = upload.id FROM upload WHERE product_image.s3_key = upload.s3_key;
UPDATE product SET video_upload_id = upload.id FROM upload WHERE product.video_key = upload.s3_key;
UPDATE product SET garment_upload_id = upload.id FROM upload WHERE product.garment_key = upload.s3_key;

-- s3_key cũ NOT NULL nên mọi row đều map được; giữ nguyên invariant.
ALTER TABLE product_image ALTER COLUMN upload_id SET NOT NULL;

ALTER TABLE product_image DROP COLUMN s3_key;
ALTER TABLE product DROP COLUMN video_key;
ALTER TABLE product DROP COLUMN garment_key;
