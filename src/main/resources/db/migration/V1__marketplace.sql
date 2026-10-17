-- V1__marketplace.sql
-- Marketplace multi-shop: shared schema + shop_id, admin duyệt shop, COD, 1-checkout-1-shop.
-- Quy ước: PK UUID gen_random_uuid() (built-in từ PG13), tiền VND dùng BIGINT (Java Long),
-- ảnh chỉ lưu s3_key (key cấp bởi S3Storage presign).

CREATE TABLE user_profile (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    avatar_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shop (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT '',
    logo_key TEXT,
    banner_key TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    approved_by UUID REFERENCES user_profile (id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shop_status ON shop (status);

CREATE TABLE shop_member (
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'STAFF')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (shop_id, user_id)
);
CREATE UNIQUE INDEX uniq_shop_single_owner ON shop_member (shop_id) WHERE role = 'OWNER';
CREATE INDEX idx_shop_member_user ON shop_member (user_id);

CREATE TABLE category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    parent_id UUID REFERENCES category (id) ON DELETE SET NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_category_parent ON category (parent_id);

CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    category_id UUID REFERENCES category (id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    slug TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'HIDDEN')),
    created_by UUID REFERENCES user_profile (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, slug),
    UNIQUE (shop_id, id)
);
CREATE INDEX idx_product_shop_status ON product (shop_id, status);
CREATE INDEX idx_product_category ON product (category_id);

CREATE TABLE product_variant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL,
    product_id UUID NOT NULL,
    sku TEXT NOT NULL,
    size TEXT NOT NULL,
    color TEXT NOT NULL,
    price BIGINT NOT NULL CHECK (price >= 0),
    compare_at_price BIGINT CHECK (compare_at_price IS NULL OR compare_at_price > price),
    stock_quantity INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_variant_product
        FOREIGN KEY (shop_id, product_id) REFERENCES product (shop_id, id) ON DELETE CASCADE,
    UNIQUE (shop_id, sku),
    UNIQUE (product_id, size, color),
    UNIQUE (product_id, id)
);
CREATE INDEX idx_variant_product ON product_variant (product_id);
CREATE UNIQUE INDEX uniq_product_default_variant ON product_variant (product_id) WHERE is_default;

CREATE TABLE product_image (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    variant_id UUID,
    s3_key TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_image_variant
        FOREIGN KEY (product_id, variant_id)
        REFERENCES product_variant (product_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_image_product ON product_image (product_id, sort_order);
CREATE INDEX idx_image_variant ON product_image (variant_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    shop_id UUID NOT NULL REFERENCES shop (id),
    buyer_id UUID NOT NULL REFERENCES user_profile (id),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED')),
    payment_method TEXT NOT NULL DEFAULT 'COD' CHECK (payment_method = 'COD'),
    payment_status TEXT NOT NULL DEFAULT 'UNPAID'
        CHECK (payment_status IN ('UNPAID', 'COLLECTED', 'FAILED')),
    subtotal BIGINT NOT NULL CHECK (subtotal >= 0),
    shipping_fee BIGINT NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    total BIGINT NOT NULL CHECK (total >= 0),
    shipping_name TEXT NOT NULL,
    shipping_phone TEXT NOT NULL,
    shipping_address TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    confirmed_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (total = subtotal + shipping_fee)
);
CREATE INDEX idx_orders_shop ON orders (shop_id, status, created_at DESC);
CREATE INDEX idx_orders_buyer ON orders (buyer_id, created_at DESC);

CREATE TABLE order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID REFERENCES product (id) ON DELETE SET NULL,
    variant_id UUID REFERENCES product_variant (id) ON DELETE SET NULL,
    product_name TEXT NOT NULL,
    variant_label TEXT NOT NULL,
    sku TEXT NOT NULL,
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    line_total BIGINT NOT NULL CHECK (line_total >= 0),
    UNIQUE (order_id, variant_id),
    CHECK (line_total = unit_price * quantity)
);
CREATE INDEX idx_order_item_variant ON order_item (variant_id);
CREATE INDEX idx_order_item_product ON order_item (product_id);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status TEXT,
    to_status TEXT NOT NULL,
    changed_by UUID REFERENCES user_profile (id),
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_history ON order_status_history (order_id, created_at);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_shop_updated BEFORE UPDATE ON shop
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_product_updated BEFORE UPDATE ON product
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_variant_updated BEFORE UPDATE ON product_variant
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_orders_updated BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
