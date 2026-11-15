-- V46__product_embedding.sql
-- Vector Fashion-CLIP theo từng ảnh sản phẩm: extension vectorscale (kéo pgvector),
-- queue pgmq embed_jobs + trigger producer. Worker repo embed-worker tiêu thụ queue.
-- Vector nullable: ảnh lỗi vẫn có row giữ embed_error; diskann bỏ qua NULL.

CREATE EXTENSION IF NOT EXISTS vectorscale CASCADE;

SELECT pgmq.create('embed_jobs')
WHERE NOT EXISTS (SELECT 1 FROM pgmq.meta WHERE queue_name = 'embed_jobs');

CREATE TABLE product_embedding (
    product_image_id UUID PRIMARY KEY REFERENCES product_image (id) ON DELETE CASCADE,
    product_id       UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    -- ON DELETE SET NULL: consumer GC của upload_gc không bị FK chặn.
    upload_id        UUID REFERENCES upload (id) ON DELETE SET NULL,
    embedding        vector(512),
    embed_error      TEXT,
    model            TEXT NOT NULL DEFAULT 'fashion-clip-vit-b32',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT product_embedding_has_result CHECK (embedding IS NOT NULL OR embed_error IS NOT NULL)
);
CREATE INDEX product_embedding_diskann
    ON product_embedding USING diskann (embedding vector_cosine_ops);
CREATE INDEX idx_product_embedding_product ON product_embedding (product_id);

CREATE OR REPLACE FUNCTION enqueue_embed_job() RETURNS trigger AS $$
DECLARE
    v_s3_key TEXT;
BEGIN
    SELECT u.s3_key INTO v_s3_key FROM upload u WHERE u.id = NEW.upload_id;
    PERFORM pgmq.send(
        'embed_jobs',
        jsonb_build_object(
            'product_image_id', NEW.id,
            'product_id', NEW.product_id,
            'upload_id', NEW.upload_id,
            's3_key', v_s3_key
        )
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_image_embed
AFTER INSERT OR UPDATE OF upload_id ON product_image
FOR EACH ROW EXECUTE FUNCTION enqueue_embed_job();

-- Backfill ảnh hiện có (seed/import) — worker xử lý dần.
SELECT pgmq.send(
    'embed_jobs',
    jsonb_build_object(
        'product_image_id', pi.id,
        'product_id', pi.product_id,
        'upload_id', pi.upload_id,
        's3_key', u.s3_key
    )
)
FROM product_image pi
JOIN upload u ON u.id = pi.upload_id;
