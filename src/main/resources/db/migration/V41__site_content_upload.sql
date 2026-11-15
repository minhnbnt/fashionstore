-- V41__site_content_upload.sql
-- 3 cột file của site content (site_banner.image_key, site_banner.mobile_image_key,
-- site_popup.image_key) tham chiếu bảng upload qua FK (design D6): thêm cột,
-- backfill join theo s3_key (V37 đã backfill đủ upload từ key legacy), rồi
-- drop cột cũ. FK không ON DELETE — xoá upload đang được tham chiếu sẽ nổ
-- constraint ("vệ sĩ" cho consumer GC).

ALTER TABLE site_banner ADD COLUMN image_upload_id UUID REFERENCES upload (id);
ALTER TABLE site_banner ADD COLUMN mobile_image_upload_id UUID REFERENCES upload (id);
ALTER TABLE site_popup ADD COLUMN image_upload_id UUID REFERENCES upload (id);

UPDATE site_banner SET image_upload_id = upload.id FROM upload WHERE site_banner.image_key = upload.s3_key;
UPDATE site_banner SET mobile_image_upload_id = upload.id FROM upload WHERE site_banner.mobile_image_key = upload.s3_key;
UPDATE site_popup SET image_upload_id = upload.id FROM upload WHERE site_popup.image_key = upload.s3_key;

-- image_key cũ NOT NULL nên mọi banner đều map được; giữ nguyên invariant.
ALTER TABLE site_banner ALTER COLUMN image_upload_id SET NOT NULL;

ALTER TABLE site_banner DROP COLUMN image_key;
ALTER TABLE site_banner DROP COLUMN mobile_image_key;
ALTER TABLE site_popup DROP COLUMN image_key;
