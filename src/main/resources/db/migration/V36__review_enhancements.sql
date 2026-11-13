ALTER TABLE review
    ADD COLUMN IF NOT EXISTS order_id UUID REFERENCES orders (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS variant_label TEXT,
    ADD COLUMN IF NOT EXISTS image_keys TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS video_key TEXT,
    ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS helpful_count INT NOT NULL DEFAULT 0 CHECK (helpful_count >= 0);

DROP INDEX IF EXISTS uq_review_one_per_buyer;

UPDATE review r
SET
    order_id = matched.order_id,
    variant_label = matched.variant_label
FROM (
    SELECT DISTINCT ON (r2.id)
        r2.id AS review_id,
        o.id AS order_id,
        NULLIF(
            string_agg(DISTINCT oi.variant_label, ', ')
                FILTER (WHERE oi.variant_label IS NOT NULL AND oi.variant_label <> ''),
            ''
        ) AS variant_label
    FROM review r2
    JOIN orders o ON o.buyer_id = r2.buyer_id
        AND o.status = 'DELIVERED'
        AND o.received_at IS NOT NULL
    JOIN order_item oi ON oi.order_id = o.id
        AND oi.product_id = r2.product_id
    WHERE r2.order_id IS NULL
    GROUP BY r2.id, o.id, o.received_at, o.created_at
    ORDER BY r2.id, o.received_at DESC, o.created_at DESC
) matched
WHERE r.id = matched.review_id
  AND r.order_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_review_order_product
    ON review (order_id, product_id)
    WHERE order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_review_product_helpful
    ON review (product_id, helpful_count DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_product_created
    ON review (product_id, created_at DESC);

CREATE TABLE IF NOT EXISTS review_helpful (
    review_id  UUID NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (review_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_review_helpful_user
    ON review_helpful (user_id);

