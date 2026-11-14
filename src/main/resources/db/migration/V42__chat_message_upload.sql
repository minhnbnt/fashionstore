-- V42__chat_message_upload.sql
-- chat_message.image_key tham chiếu bảng upload qua FK (design D6): thêm cột,
-- backfill join theo s3_key (V37 đã backfill đủ upload từ key legacy), rồi
-- drop cột cũ. image_key cũ nullable nên image_upload_id giữ nullable —
-- tin nhắn chỉ có chữ thì không có ảnh. FK không ON DELETE — xoá upload
-- đang được tham chiếu sẽ nổ constraint ("vệ sĩ" cho consumer GC).

ALTER TABLE chat_message ADD COLUMN image_upload_id UUID REFERENCES upload (id);

UPDATE chat_message SET image_upload_id = upload.id FROM upload WHERE chat_message.image_key = upload.s3_key;

ALTER TABLE chat_message DROP COLUMN image_key;
