-- Cấu hình chung của sàn (key-value) và lịch sử thay đổi cấu hình của admin.

CREATE TABLE system_setting (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL
);

CREATE TABLE admin_config_audit (
    id UUID PRIMARY KEY,
    entity TEXT NOT NULL CHECK (entity IN ('CARRIER', 'SHIPPING_RATE', 'PAYMENT_GATEWAY', 'PAYMENT_TRANSACTION', 'SETTING')),
    entity_key TEXT NOT NULL,
    action TEXT NOT NULL,
    old_value JSONB,
    new_value JSONB,
    changed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_config_audit ON admin_config_audit (entity, changed_at DESC);

INSERT INTO system_setting (key, value) VALUES
    ('site.name', 'nét'),
    ('site.hotline', '1900 0000'),
    ('site.support_email', 'support@fashionstore.local'),
    ('shipping.free_threshold', '500000'),
    ('shipping.base_items', '2'),
    ('order.payment_hold_minutes', '15'),
    ('order.auto_complete_days', '7'),
    ('order.cod_min_total', '0'),
    ('order.cod_max_total', '10000000');
