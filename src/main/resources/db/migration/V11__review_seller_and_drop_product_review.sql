-- Seller reply/report tools move onto the review table (single review system,
-- replaces product_review from V8__seller_center).

ALTER TABLE review ADD COLUMN seller_reply TEXT;
ALTER TABLE review ADD COLUMN replied_at TIMESTAMPTZ;
ALTER TABLE review ADD COLUMN is_reported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE review ADD COLUMN report_reason TEXT;

DROP TABLE product_review;
