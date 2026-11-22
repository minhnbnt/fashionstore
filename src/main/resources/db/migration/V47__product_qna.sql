CREATE TABLE product_question (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    product_id UUID NOT NULL,
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    asker_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    asker_display_name TEXT NOT NULL,
    content TEXT NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 1000),
    status TEXT NOT NULL DEFAULT 'UNANSWERED'
        CHECK (status IN ('UNANSWERED', 'ANSWERED', 'CLOSED')),
    closed_at TIMESTAMPTZ,
    closed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    edited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_product_question_product
        FOREIGN KEY (shop_id, product_id) REFERENCES product (shop_id, id) ON DELETE CASCADE
);

CREATE TABLE product_answer (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES product_question (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    author_display_name TEXT NOT NULL,
    author_role TEXT NOT NULL CHECK (author_role IN ('SHOP', 'VERIFIED_BUYER')),
    verified_buyer BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 1000),
    edited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_product_answer_verified_role
        CHECK (
            (author_role = 'VERIFIED_BUYER' AND verified_buyer = TRUE)
            OR (author_role = 'SHOP' AND verified_buyer = FALSE)
        )
);

CREATE INDEX idx_product_question_product_created
    ON product_question (product_id, created_at DESC, id DESC);
CREATE INDEX idx_product_question_shop_status_created
    ON product_question (shop_id, status, created_at DESC, id DESC);
CREATE INDEX idx_product_question_shop_product_created
    ON product_question (shop_id, product_id, created_at DESC);
CREATE INDEX idx_product_answer_question_created
    ON product_answer (question_id, created_at ASC, id ASC);

CREATE UNIQUE INDEX uq_notification_qna_dedup
    ON user_notification (user_id, type, ref_id)
    WHERE ref_id IS NOT NULL AND type IN ('QNA_NEW_QUESTION', 'QNA_NEW_ANSWER');
