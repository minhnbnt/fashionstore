-- V8__seller_center.sql
-- Seller Center comprehensive feature set:
-- Shop profile & KYC, Follower, Soft delete & Stock movement,
-- Return/Refund, Settlements & Revenue, Reviews & Replies,
-- Chat, Shop decoration layout builder, Staff RBAC & Invitations.

-- 1. Shop profile & KYC fields
ALTER TABLE shop ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS return_policy TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS processing_days SMALLINT DEFAULT 2;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS seller_type TEXT NOT NULL DEFAULT 'INDIVIDUAL'
    CHECK (seller_type IN ('INDIVIDUAL', 'CORPORATE'));
ALTER TABLE shop ADD COLUMN IF NOT EXISTS tax_code TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS id_number TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS id_doc_key TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS business_cert_key TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS rating NUMERIC(3,2);
ALTER TABLE shop ADD COLUMN IF NOT EXISTS rating_count INTEGER DEFAULT 0;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS follower_count INTEGER DEFAULT 0;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS bank_name TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS bank_account TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS bank_holder TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS auto_reply_text TEXT;
ALTER TABLE shop ADD COLUMN IF NOT EXISTS working_hours TEXT;

-- Shop followers
CREATE TABLE IF NOT EXISTS shop_follower (
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (shop_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_shop_follower_user ON shop_follower (user_id);

-- 2. Product status DELETED & low stock threshold
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_status_check;
ALTER TABLE product ADD CONSTRAINT product_status_check CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'));

ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER DEFAULT 5;

CREATE TABLE IF NOT EXISTS stock_movement (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL REFERENCES product_variant(id) ON DELETE CASCADE,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    delta INTEGER NOT NULL,
    reason TEXT NOT NULL,
    reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stock_movement_variant ON stock_movement (variant_id);
CREATE INDEX IF NOT EXISTS idx_stock_movement_shop ON stock_movement (shop_id);

-- 3. Order cancel notes & return/refund
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancel_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancel_note TEXT;

CREATE TABLE IF NOT EXISTS return_request (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    buyer_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    description TEXT,
    evidence_keys TEXT[],
    status TEXT NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED')),
    resolution TEXT,
    refund_amount BIGINT,
    seller_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_return_request_shop ON return_request (shop_id);
CREATE INDEX IF NOT EXISTS idx_return_request_order ON return_request (order_id);
CREATE INDEX IF NOT EXISTS idx_return_request_buyer ON return_request (buyer_id);

-- 4. Revenue settlement & withdrawal
CREATE TABLE IF NOT EXISTS seller_settlement (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    gross_amount BIGINT NOT NULL,
    platform_fee BIGINT NOT NULL DEFAULT 0,
    net_amount BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'HOLDING'
        CHECK (status IN ('HOLDING', 'RELEASED', 'WITHDRAWN')),
    hold_until TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_seller_settlement_shop ON seller_settlement (shop_id);

CREATE TABLE IF NOT EXISTS seller_withdrawal (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    amount BIGINT NOT NULL,
    bank_name TEXT NOT NULL,
    bank_account TEXT NOT NULL,
    bank_holder TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_seller_withdrawal_shop ON seller_withdrawal (shop_id);

-- 5. Product reviews & ratings
CREATE TABLE IF NOT EXISTS product_review (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    buyer_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    image_keys TEXT[],
    seller_reply TEXT,
    replied_at TIMESTAMPTZ,
    is_reported BOOLEAN NOT NULL DEFAULT FALSE,
    report_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_product_review_product ON product_review (product_id);
CREATE INDEX IF NOT EXISTS idx_product_review_shop ON product_review (shop_id);

-- Chat conversations & messages
CREATE TABLE IF NOT EXISTS chat_conversation (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    buyer_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    last_message_text TEXT,
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    seller_unread_count INT NOT NULL DEFAULT 0,
    buyer_unread_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, buyer_id)
);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_shop ON chat_conversation (shop_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_buyer ON chat_conversation (buyer_id);

CREATE TABLE IF NOT EXISTS chat_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_conversation(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('BUYER', 'SELLER')),
    content TEXT NOT NULL,
    image_key TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation ON chat_message (conversation_id, sent_at);

-- 6. Shop decoration
CREATE TABLE IF NOT EXISTS shop_decoration (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL UNIQUE REFERENCES shop(id) ON DELETE CASCADE,
    layout TEXT NOT NULL DEFAULT '[]',
    draft TEXT,
    published_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. Staff roles & internal permissions
CREATE TABLE IF NOT EXISTS shop_role (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    permissions TEXT[] NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(shop_id, name)
);
CREATE INDEX IF NOT EXISTS idx_shop_role_shop ON shop_role (shop_id);

ALTER TABLE shop_member ADD COLUMN IF NOT EXISTS role_id UUID REFERENCES shop_role(id) ON DELETE SET NULL;
ALTER TABLE shop_member ADD COLUMN IF NOT EXISTS invited_by UUID REFERENCES user_profile(id) ON DELETE SET NULL;
ALTER TABLE shop_member ADD COLUMN IF NOT EXISTS invited_at TIMESTAMPTZ;
ALTER TABLE shop_member ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('PENDING', 'ACTIVE', 'DEACTIVATED'));

CREATE TABLE IF NOT EXISTS shop_invitation (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    role_id UUID NOT NULL REFERENCES shop_role(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_shop_invitation_shop ON shop_invitation (shop_id);
CREATE INDEX IF NOT EXISTS idx_shop_invitation_token ON shop_invitation (token);

CREATE TABLE IF NOT EXISTS shop_member_audit (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    target_type TEXT,
    target_id UUID,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_shop_audit_shop ON shop_member_audit (shop_id);
