-- V39__shop_kyc_upload.sql
-- 7 cột file của shop (logo, banner, id_doc, business_cert, id_front, id_back, id_selfie)
-- tham chiếu bảng upload qua FK (design D6): thêm cột, backfill join theo s3_key
-- (V37 đã backfill đủ upload từ key legacy), rồi drop cột cũ. FK không ON DELETE —
-- xoá upload đang được tham chiếu sẽ nổ constraint ("vệ sĩ" cho consumer GC).

ALTER TABLE shop ADD COLUMN logo_upload_id UUID REFERENCES upload (id);
ALTER TABLE shop ADD COLUMN banner_upload_id UUID REFERENCES upload (id);
ALTER TABLE shop ADD COLUMN id_doc_upload_id UUID REFERENCES upload (id);
ALTER TABLE shop ADD COLUMN business_cert_upload_id UUID REFERENCES upload (id);
ALTER TABLE shop ADD COLUMN id_front_upload_id UUID REFERENCES upload (id);
ALTER TABLE shop ADD COLUMN id_back_upload_id UUID REFERENCES upload (id);
ALTER TABLE shop ADD COLUMN id_selfie_upload_id UUID REFERENCES upload (id);

UPDATE shop SET logo_upload_id = upload.id FROM upload WHERE shop.logo_key = upload.s3_key;
UPDATE shop SET banner_upload_id = upload.id FROM upload WHERE shop.banner_key = upload.s3_key;
UPDATE shop SET id_doc_upload_id = upload.id FROM upload WHERE shop.id_doc_key = upload.s3_key;
UPDATE shop SET business_cert_upload_id = upload.id FROM upload WHERE shop.business_cert_key = upload.s3_key;
UPDATE shop SET id_front_upload_id = upload.id FROM upload WHERE shop.id_front_key = upload.s3_key;
UPDATE shop SET id_back_upload_id = upload.id FROM upload WHERE shop.id_back_key = upload.s3_key;
UPDATE shop SET id_selfie_upload_id = upload.id FROM upload WHERE shop.id_selfie_key = upload.s3_key;

ALTER TABLE shop DROP COLUMN logo_key;
ALTER TABLE shop DROP COLUMN banner_key;
ALTER TABLE shop DROP COLUMN id_doc_key;
ALTER TABLE shop DROP COLUMN business_cert_key;
ALTER TABLE shop DROP COLUMN id_front_key;
ALTER TABLE shop DROP COLUMN id_back_key;
ALTER TABLE shop DROP COLUMN id_selfie_key;
