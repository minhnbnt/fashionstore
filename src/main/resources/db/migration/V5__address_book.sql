CREATE TABLE address (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    house_number TEXT,
    street TEXT,
    ward TEXT,
    district TEXT,
    city TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_address_one_default_per_user ON address (user_id) WHERE is_default = TRUE;
CREATE INDEX idx_address_user ON address (user_id);
