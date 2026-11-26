-- V49__vietqr_escrow_payment.sql
-- Thanh toán trực tuyến VietQR (NAPAS), giữ tiền trung gian (Escrow) và đối soát hoàn tiền.

CREATE TABLE payment_gateway (
    code TEXT PRIMARY KEY CHECK (code IN ('COD', 'VIETQR')),
    name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    bank_bin TEXT,
    bank_code TEXT,
    bank_name TEXT,
    account_number TEXT,
    account_name TEXT,
    webhook_secret TEXT,
    sandbox_mode BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL
);

INSERT INTO payment_gateway (
    code, name, enabled, bank_bin, bank_code, bank_name,
    account_number, account_name, webhook_secret, sandbox_mode
) VALUES
    (
        'COD',
        'Thanh toán khi nhận hàng (COD)',
        TRUE,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        FALSE
    ),
    (
        'VIETQR',
        'Chuyển khoản ngân hàng (VietQR)',
        TRUE,
        '970422',
        'MB',
        'Ngân hàng TMCP Quân đội (MB Bank)',
        '0900123456789',
        'SAN TMDT FASHIONSTORE',
        'demo-vietqr-secret-key',
        TRUE
    );

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('AWAITING_PAYMENT', 'PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'));

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_method_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check
    CHECK (payment_method IN ('COD', 'VIETQR'));

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check
    CHECK (payment_status IN ('UNPAID', 'PAID', 'COLLECTED', 'FAILED', 'REFUND_PENDING', 'REFUNDED'));

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS placed_at TIMESTAMPTZ;

UPDATE orders SET placed_at = created_at WHERE placed_at IS NULL AND status <> 'AWAITING_PAYMENT';

CREATE INDEX IF NOT EXISTS idx_orders_awaiting_expiry
    ON orders (payment_expires_at) WHERE status = 'AWAITING_PAYMENT';

CREATE TABLE payment_transaction (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES user_profile (id),
    provider TEXT NOT NULL DEFAULT 'VIETQR' CHECK (provider IN ('VIETQR')),
    txn_ref TEXT NOT NULL UNIQUE,
    amount BIGINT NOT NULL CHECK (amount >= 0),
    received_amount BIGINT NOT NULL DEFAULT 0 CHECK (received_amount >= 0),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'UNDERPAID', 'FAILED', 'EXPIRED')),
    bank_bin TEXT NOT NULL,
    bank_code TEXT NOT NULL,
    account_number TEXT NOT NULL,
    account_name TEXT NOT NULL,
    qr_image_url TEXT NOT NULL,
    bank_transaction_id TEXT,
    transfer_content TEXT,
    paid_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    refund_status TEXT NOT NULL DEFAULT 'NONE'
        CHECK (refund_status IN ('NONE', 'PENDING', 'REFUNDED')),
    refund_amount BIGINT NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
    refund_reason TEXT,
    refunded_at TIMESTAMPTZ,
    refunded_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    refund_note TEXT,
    raw_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_transaction_order (
    transaction_id UUID NOT NULL REFERENCES payment_transaction (id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, order_id)
);
CREATE INDEX idx_pto_order ON payment_transaction_order (order_id);

ALTER TABLE seller_settlement DROP CONSTRAINT IF EXISTS seller_settlement_status_check;
ALTER TABLE seller_settlement ADD CONSTRAINT seller_settlement_status_check
    CHECK (status IN ('HOLDING', 'RELEASED', 'WITHDRAWN', 'CANCELLED'));
