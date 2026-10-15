ALTER TABLE orders ADD COLUMN received_at TIMESTAMPTZ;

CREATE TABLE review (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    buyer_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    buyer_display_name TEXT NOT NULL,
    stars INT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_review_one_per_buyer ON review (product_id, buyer_id);
CREATE INDEX idx_review_product ON review (product_id);
