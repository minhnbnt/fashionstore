-- V28__seller_staff_management.sql
-- Đặc tả 2.8.1: lời mời nhân viên qua tài khoản, thông báo trong website,
-- index audit theo shop và bộ quyền 5 nhóm cho 4 vai trò hệ thống.

ALTER TABLE shop_invitation ADD COLUMN invitee_id UUID REFERENCES user_profile(id) ON DELETE CASCADE;
ALTER TABLE shop_invitation ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_shop_invitation_pending
    ON shop_invitation (shop_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE TABLE user_notification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    link TEXT,
    ref_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON user_notification (user_id, read_at, created_at DESC);

CREATE INDEX idx_shop_audit_shop_created ON shop_member_audit (shop_id, created_at DESC);

UPDATE shop_role SET permissions = ARRAY[
    'PRODUCT_READ','PRODUCT_WRITE','INVENTORY_READ','INVENTORY_WRITE',
    'ORDER_READ','ORDER_PROCESS','ORDER_CANCEL','RETURN_PROCESS',
    'REVENUE_READ','REVENUE_WITHDRAW','REVIEW_READ','REVIEW_REPLY',
    'CHAT_ACCESS','DECORATION_WRITE'
] WHERE is_system = TRUE AND name = 'Quản lý (Manager)';

UPDATE shop_role SET permissions = ARRAY['ORDER_READ','REVIEW_READ','REVIEW_REPLY','CHAT_ACCESS']
WHERE is_system = TRUE AND name = 'Chăm sóc khách hàng (CSKH)';

UPDATE shop_role SET permissions = ARRAY['ORDER_READ','ORDER_PROCESS','INVENTORY_READ','INVENTORY_WRITE']
WHERE is_system = TRUE AND name = 'Kho vận (Warehouse)';

UPDATE shop_role SET permissions = ARRAY['PRODUCT_READ','DECORATION_WRITE','REVENUE_READ']
WHERE is_system = TRUE AND name = 'Marketing';
