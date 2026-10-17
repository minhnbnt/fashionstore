CREATE TABLE wishlist_item (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_wishlist_one_per_product ON wishlist_item (buyer_id, product_id);
CREATE INDEX idx_wishlist_buyer ON wishlist_item (buyer_id);
