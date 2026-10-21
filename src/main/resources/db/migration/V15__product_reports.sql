-- V15__product_reports.sql
-- Nguồn phát hiện sản phẩm vi phạm: người mua báo cáo (USER) + hệ thống gắn cờ theo từ khóa cấm (SYSTEM).

CREATE TABLE product_report (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    source TEXT NOT NULL CHECK (source IN ('USER', 'SYSTEM')),
    -- NULL với cờ hệ thống, hoặc khi tài khoản người báo cáo bị xóa.
    reporter_id UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    reason TEXT NOT NULL
        CHECK (reason IN (
            'PROHIBITED', 'COUNTERFEIT', 'IP_INFRINGEMENT', 'MISLEADING', 'WRONG_CATEGORY', 'SPAM', 'OTHER'
        )),
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACTION_TAKEN', 'DISMISSED')),
    resolved_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Mỗi người 1 báo cáo đang mở / sản phẩm; mỗi sản phẩm tối đa 1 cờ hệ thống đang mở.
CREATE UNIQUE INDEX uq_product_report_open_user
    ON product_report (product_id, reporter_id) WHERE status = 'OPEN' AND source = 'USER';
CREATE UNIQUE INDEX uq_product_report_open_system
    ON product_report (product_id) WHERE status = 'OPEN' AND source = 'SYSTEM';
CREATE INDEX idx_product_report_open ON product_report (product_id, created_at) WHERE status = 'OPEN';
CREATE INDEX idx_product_report_product ON product_report (product_id, created_at DESC);

CREATE TABLE banned_keyword (
    id UUID PRIMARY KEY,
    keyword TEXT NOT NULL CHECK (length(keyword) BETWEEN 2 AND 100),
    violation_type TEXT NOT NULL
        CHECK (violation_type IN (
            'PROHIBITED', 'COUNTERFEIT', 'IP_INFRINGEMENT', 'MISLEADING', 'WRONG_CATEGORY', 'SPAM', 'OTHER'
        )),
    created_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_banned_keyword ON banned_keyword (lower(keyword));
