CREATE TABLE review_reply_template (
    id         UUID PRIMARY KEY,
    shop_id    UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    content    TEXT NOT NULL,
    created_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_review_reply_template_title_len CHECK (char_length(btrim(title)) BETWEEN 1 AND 100),
    CONSTRAINT chk_review_reply_template_content_len CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000)
);

CREATE UNIQUE INDEX uq_review_reply_template_shop_title
    ON review_reply_template (shop_id, lower(btrim(title)));

CREATE INDEX idx_review_reply_template_shop_updated
    ON review_reply_template (shop_id, updated_at DESC, created_at DESC);

CREATE INDEX idx_review_created_at
    ON review (created_at DESC);

