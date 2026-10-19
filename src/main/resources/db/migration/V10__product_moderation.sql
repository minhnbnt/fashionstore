-- V10__product_moderation.sql
-- Module quản trị: admin gỡ sản phẩm vi phạm (BANNED) kèm loại vi phạm + lý do, lịch sử kiểm duyệt.

ALTER TABLE product DROP CONSTRAINT IF EXISTS product_status_check;
ALTER TABLE product
    ADD CONSTRAINT product_status_check CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED', 'BANNED'));

-- violation_type + status_reason: lần gỡ gần nhất, NULL khi không bị gỡ.
ALTER TABLE product
    ADD COLUMN violation_type TEXT
        CHECK (violation_type IN (
            'PROHIBITED', 'COUNTERFEIT', 'IP_INFRINGEMENT', 'MISLEADING', 'WRONG_CATEGORY', 'SPAM', 'OTHER'
        )),
    ADD COLUMN status_reason TEXT;

CREATE INDEX idx_product_status_updated ON product (status, updated_at DESC);

CREATE TABLE product_moderation_history (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    violation_type TEXT,
    changed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_moderation_history ON product_moderation_history (product_id, created_at);
