-- V44__return_evidence_upload.sql
-- return_request.evidence_keys[] tách thành bảng con return_evidence (design D4:
-- FK thật thay vì mảng key — Postgres không enforce FK trên phần tử mảng, rác
-- tham chiếu chết không bị DB chặn): mỗi file bằng chứng là 1 row tham chiếu
-- upload qua FK, sort_order theo vị trí trong mảng. FK CASCADE theo
-- return_request_id (evidence sống chết theo yêu cầu trả hàng); FK upload không
-- ON DELETE — xoá upload đang được tham chiếu sẽ nổ constraint ("vệ sĩ" cho
-- consumer GC).

CREATE TABLE return_evidence (
    id                UUID PRIMARY KEY,
    return_request_id UUID NOT NULL REFERENCES return_request (id) ON DELETE CASCADE,
    upload_id         UUID NOT NULL REFERENCES upload (id),
    sort_order        INT NOT NULL
);

CREATE INDEX idx_return_evidence_request ON return_evidence (return_request_id, sort_order);

-- Backfill: 1 phần tử mảng = 1 row, giữ thứ tự vị trí trong mảng (ordinality bắt đầu từ 1).
INSERT INTO return_evidence (id, return_request_id, upload_id, sort_order)
SELECT gen_random_uuid(), r.id, u.id, k.ord - 1
FROM return_request r
CROSS JOIN LATERAL unnest(r.evidence_keys) WITH ORDINALITY AS k(key, ord)
JOIN upload u ON u.s3_key = k.key;

ALTER TABLE return_request DROP COLUMN evidence_keys;
