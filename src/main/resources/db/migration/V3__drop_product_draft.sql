-- V3__drop_product_draft.sql
-- Bo DRAFT: product tao phat ACTIVE luon (shop APPROVED da la gate).
-- Trang thai con lai: ACTIVE <-> HIDDEN. HIDDEN dung de go hang.

ALTER TABLE product DROP CONSTRAINT product_status_check;

ALTER TABLE product
    ADD CONSTRAINT product_status_check CHECK (status IN ('ACTIVE', 'HIDDEN'));

ALTER TABLE product ALTER COLUMN status SET DEFAULT 'ACTIVE';
