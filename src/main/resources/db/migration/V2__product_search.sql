-- V2__product_search.sql
-- BM25 full-text search cho product bằng pg_textsearch (image project đã preload sẵn).
--
-- text_config = 'simple' vì stock PG không có stemmer tiếng Việt. 'simple' chỉ
-- lowercase + tách token theo khoảng trắng, đủ cho phase 1. Tiếng Việt không cần
-- stemming (ngôn ngữ đơn lập), nhưng từ ghép ("áo dài") bị tách thành token rời,
-- BM25 vẫn xếp hạng ổn nhờ cộng điểm từng token.
--
-- Partial index WHERE status = 'ACTIVE' để bản nháp/bị ẩn không làm bẩn IDF.
-- Vì là partial index, query BẮT BUỘC dùng to_bm25query với tên index tường minh:
--
--   SELECT id, name
--   FROM product
--   WHERE status = 'ACTIVE' AND shop_id = ?
--   ORDER BY (coalesce(name, '') || ' ' || coalesce(description, ''))
--       <@> to_bm25query('ao thun den', 'idx_product_bm25')
--   LIMIT 20;
--
-- Lọc boolean kết hợp xếp hạng (cần default_text_search_config khớp index):
--
--   SET LOCAL default_text_search_config = 'simple';
--   SELECT id, name
--   FROM product
--   WHERE status = 'ACTIVE'
--     AND (coalesce(name, '') || ' ' || coalesce(description, ''))
--         @@ to_tsquery('simple', 'ao & thun')
--   ORDER BY (coalesce(name, '') || ' ' || coalesce(description, ''))
--       <@> to_bm25query('ao thun', 'idx_product_bm25')
--   LIMIT 20;

CREATE EXTENSION IF NOT EXISTS pg_textsearch;

CREATE INDEX idx_product_bm25 ON product USING bm25
    ((coalesce(name, '') || ' ' || coalesce(description, '')))
    WITH (text_config = 'simple')
    WHERE status = 'ACTIVE';
