CREATE TABLE shop_voucher (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    discount_type TEXT NOT NULL
        CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    discount_value BIGINT NOT NULL CHECK (discount_value > 0),
    max_discount_amount BIGINT CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
    min_order_subtotal BIGINT NOT NULL DEFAULT 0 CHECK (min_order_subtotal >= 0),
    product_scope TEXT NOT NULL DEFAULT 'ALL_PRODUCTS'
        CHECK (product_scope IN ('ALL_PRODUCTS', 'SPECIFIC_PRODUCTS')),
    visibility TEXT NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    usage_limit INT NOT NULL CHECK (usage_limit > 0),
    used_count INT NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= usage_limit),
    per_buyer_limit INT NOT NULL DEFAULT 1 CHECK (per_buyer_limit > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, code),
    CHECK (ends_at > starts_at),
    CHECK (discount_type <> 'PERCENTAGE' OR (discount_value >= 1 AND discount_value <= 99))
);

CREATE INDEX idx_shop_voucher_shop_active
    ON shop_voucher (shop_id, active, starts_at, ends_at);

CREATE TABLE shop_voucher_product (
    voucher_id UUID NOT NULL REFERENCES shop_voucher (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    PRIMARY KEY (voucher_id, product_id)
);
CREATE INDEX idx_svp_product ON shop_voucher_product (product_id);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_id UUID REFERENCES shop_voucher (id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_code TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount BIGINT NOT NULL DEFAULT 0
    CHECK (discount_amount >= 0);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_check;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_total_math_check;
ALTER TABLE orders ADD CONSTRAINT orders_total_math_check
    CHECK (discount_amount <= subtotal AND total = subtotal + shipping_fee - discount_amount);

CREATE INDEX IF NOT EXISTS idx_orders_buyer_voucher
    ON orders (buyer_id, voucher_id)
    WHERE voucher_id IS NOT NULL AND status <> 'CANCELLED';
