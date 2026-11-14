-- V38__user_profile_avatar_upload.sql
-- Avatar tham chiếu bảng upload qua FK (design D6): thêm cột, backfill join theo s3_key
-- (V37 đã backfill đủ upload từ mọi key legacy), rồi drop cột cũ. FK không ON DELETE —
-- xoá upload đang được tham chiếu sẽ nổ constraint ("vệ sĩ" cho consumer GC).

ALTER TABLE user_profile ADD COLUMN avatar_upload_id UUID REFERENCES upload (id);

UPDATE user_profile
SET avatar_upload_id = upload.id
FROM upload
WHERE user_profile.avatar_key = upload.s3_key;

ALTER TABLE user_profile DROP COLUMN avatar_key;
