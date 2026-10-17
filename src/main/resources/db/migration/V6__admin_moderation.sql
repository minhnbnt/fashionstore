-- V6__admin_moderation.sql
-- Module quản trị: khóa tài khoản, kiểm duyệt shop có lý do, audit trail trạng thái.
-- V4 bỏ trống (V5 đã chạy trước), không chèn lại V4.

ALTER TABLE user_profile
    ADD COLUMN role TEXT NOT NULL DEFAULT 'CUSTOMER'
        CHECK (role IN ('CUSTOMER', 'SHOP', 'ADMIN')),
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'LOCKED')),
    ADD COLUMN status_reason TEXT;
CREATE INDEX idx_user_profile_status ON user_profile (status, created_at DESC);

-- status_reason: lý do REJECTED/SUSPENDED gần nhất, NULL khi PENDING/APPROVED.
-- submitted_at: lần nộp hồ sơ gần nhất, hàng đợi duyệt xếp FIFO theo cột này.
ALTER TABLE shop
    ADD COLUMN status_reason TEXT,
    ADD COLUMN submitted_at TIMESTAMPTZ;
UPDATE shop SET submitted_at = created_at;
ALTER TABLE shop
    ALTER COLUMN submitted_at SET NOT NULL,
    ALTER COLUMN submitted_at SET DEFAULT now();
DROP INDEX idx_shop_status;
CREATE INDEX idx_shop_status ON shop (status, submitted_at);

CREATE TABLE shop_status_history (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    from_status TEXT,
    to_status TEXT NOT NULL,
    changed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shop_status_history ON shop_status_history (shop_id, created_at);

CREATE TABLE user_status_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    from_status TEXT,
    to_status TEXT NOT NULL,
    changed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_status_history ON user_status_history (user_id, created_at);
