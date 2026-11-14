-- V43__review_media_upload.sql
-- review.image_keys[] + video_key tách thành bảng con review_media (design D4:
-- FK thật thay vì mảng key — Postgres không enforce FK trên phần tử mảng, rác
-- tham chiếu chết không bị DB chặn): mỗi ảnh là 1 row IMAGE với sort_order theo
-- vị trí trong mảng, video là 1 row VIDEO xếp sau toàn bộ ảnh. FK CASCADE theo
-- review_id (media sống chết theo review); FK upload không ON DELETE — xoá
-- upload đang được tham chiếu sẽ nổ constraint ("vệ sĩ" cho consumer GC).

CREATE TABLE review_media (
    id         UUID PRIMARY KEY,
    review_id  UUID NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    upload_id  UUID NOT NULL REFERENCES upload (id),
    kind       TEXT NOT NULL CHECK (kind IN ('IMAGE', 'VIDEO')),
    sort_order INT NOT NULL
);

CREATE INDEX idx_review_media_review ON review_media (review_id, sort_order);

-- Backfill ảnh: 1 phần tử mảng = 1 row, giữ thứ tự vị trí trong mảng (ordinality bắt đầu từ 1).
INSERT INTO review_media (id, review_id, upload_id, kind, sort_order)
SELECT gen_random_uuid(), r.id, u.id, 'IMAGE', k.ord - 1
FROM review r
CROSS JOIN LATERAL unnest(r.image_keys) WITH ORDINALITY AS k(key, ord)
JOIN upload u ON u.s3_key = k.key;

-- Backfill video: tối đa 1 video/review, sort_order = số ảnh để xếp sau.
INSERT INTO review_media (id, review_id, upload_id, kind, sort_order)
SELECT gen_random_uuid(), r.id, u.id, 'VIDEO', cardinality(r.image_keys)
FROM review r
JOIN upload u ON u.s3_key = r.video_key
WHERE r.video_key IS NOT NULL;

ALTER TABLE review DROP COLUMN image_keys;
ALTER TABLE review DROP COLUMN video_key;
