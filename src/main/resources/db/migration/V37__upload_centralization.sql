-- V37__upload_centralization.sql
-- Tập trung mọi S3 key rải rác vào bảng upload (design D1): backfill từ các cột/mảng key
-- của user_profile, shop, product_image, product, site_banner, site_popup, chat_message,
-- review, return_request; extension pgmq + queue upload_gc + trigger producer (design D5).

CREATE TABLE upload (
    id           UUID PRIMARY KEY,
    s3_key       TEXT NOT NULL UNIQUE,
    content_type TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Producer GC: extension có sẵn trong image Postgres của project, fail-loud nếu thiếu.
CREATE EXTENSION IF NOT EXISTS pgmq;

SELECT pgmq.create('upload_gc')
WHERE NOT EXISTS (SELECT 1 FROM pgmq.meta WHERE queue_name = 'upload_gc');

CREATE OR REPLACE FUNCTION enqueue_upload_gc() RETURNS trigger AS $$
BEGIN
    PERFORM pgmq.send(
        'upload_gc',
        jsonb_build_object('upload_id', NEW.id, 's3_key', NEW.s3_key),
        86400
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_upload_gc
AFTER INSERT ON upload
FOR EACH ROW EXECUTE FUNCTION enqueue_upload_gc();

-- Backfill dedupe: mỗi key distinct thành 1 row upload. Trigger phía trên bắn message
-- cho cả row legacy — vô hại, message nằm chờ consumer (design D5).
-- Content-type suy từ extension: key do S3Storage sinh luôn là uuid.ext.
INSERT INTO upload (id, s3_key, content_type)
SELECT
    gen_random_uuid(),
    k.key,
    CASE lower(substring(k.key from '\.([a-z0-9-]+)$'))
        WHEN 'jpeg' THEN 'image/jpeg'
        WHEN 'jpg'  THEN 'image/jpeg'
        WHEN 'png'  THEN 'image/png'
        WHEN 'webp' THEN 'image/webp'
        WHEN 'gif'  THEN 'image/gif'
        WHEN 'avif' THEN 'image/avif'
        WHEN 'svg'  THEN 'image/svg+xml'
        WHEN 'mp4'  THEN 'video/mp4'
        WHEN 'webm' THEN 'video/webm'
        WHEN 'mov'  THEN 'video/quicktime'
        ELSE 'application/octet-stream'
    END
FROM (
    SELECT avatar_key AS key FROM user_profile WHERE avatar_key IS NOT NULL
    UNION
    SELECT logo_key FROM shop WHERE logo_key IS NOT NULL
    UNION
    SELECT banner_key FROM shop WHERE banner_key IS NOT NULL
    UNION
    SELECT id_doc_key FROM shop WHERE id_doc_key IS NOT NULL
    UNION
    SELECT business_cert_key FROM shop WHERE business_cert_key IS NOT NULL
    UNION
    SELECT id_front_key FROM shop WHERE id_front_key IS NOT NULL
    UNION
    SELECT id_back_key FROM shop WHERE id_back_key IS NOT NULL
    UNION
    SELECT id_selfie_key FROM shop WHERE id_selfie_key IS NOT NULL
    UNION
    SELECT s3_key FROM product_image
    UNION
    SELECT video_key FROM product WHERE video_key IS NOT NULL
    UNION
    SELECT garment_key FROM product WHERE garment_key IS NOT NULL
    UNION
    SELECT image_key FROM site_banner
    UNION
    SELECT mobile_image_key FROM site_banner WHERE mobile_image_key IS NOT NULL
    UNION
    SELECT image_key FROM site_popup WHERE image_key IS NOT NULL
    UNION
    SELECT image_key FROM chat_message WHERE image_key IS NOT NULL
    UNION
    SELECT unnest(image_keys) FROM review WHERE array_length(image_keys, 1) > 0
    UNION
    SELECT video_key FROM review WHERE video_key IS NOT NULL
    UNION
    SELECT unnest(evidence_keys) FROM return_request WHERE array_length(evidence_keys, 1) > 0
) AS k
ON CONFLICT (s3_key) DO NOTHING;
