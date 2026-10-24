-- V27__product_audit_log.sql
-- Đặc tả 2.2.2: Lịch sử chỉnh sửa sản phẩm (Audit Log)

CREATE TABLE product_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    changed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    changes_summary TEXT NOT NULL,
    old_values TEXT,
    new_values TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_audit_log ON product_audit_log (product_id, created_at DESC);
CREATE INDEX idx_product_audit_shop ON product_audit_log (shop_id, created_at DESC);
